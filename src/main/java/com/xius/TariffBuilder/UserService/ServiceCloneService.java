package com.xius.TariffBuilder.UserService;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

import com.xius.TariffBuilder.exception.TariffInsertException;

@Service
public class ServiceCloneService {

        private static final Logger logger = LoggerFactory.getLogger(ServiceCloneService.class);

        @Qualifier("oracleJdbcTemplate")
        private final JdbcTemplate jdbcTemplate;

        private final ServiceplanZone servicePlanZone;

        public static class CloneServiceResult {

                private Long newPackageId;
                private Long newPlanId;
                private Long newPlanZoneId;

                public CloneServiceResult(Long newPackageId, Long newPlanId, Long newPlanZoneId) {
                        this.newPackageId = newPackageId;
                        this.newPlanId = newPlanId;
                        this.newPlanZoneId = newPlanZoneId;
                }

                public Long getNewPackageId() {
                        return newPackageId;
                }

                public Long getNewPlanId() {
                        return newPlanId;
                }

                public Long getNewPlanZoneId() {
                        return newPlanZoneId;
                }
        }

        ServiceCloneService(JdbcTemplate jdbcTemplate, ServiceplanZone servicePlanZone) {
                this.jdbcTemplate = jdbcTemplate;
                this.servicePlanZone = servicePlanZone;
        }

        // Check TP name already exists.
        public boolean isTpNameExists(Long networkId, String tpName) {

                String pkgSql = """
                                select count(*)
                                from CS_RAT_SERVICE_PACKAGE
                                where NETWORK_ID = ?
                                and SERVICE_PACKAGE_DESC like '%' || ?
                                """;

                Integer pkgCount = jdbcTemplate.queryForObject(
                                pkgSql,
                                Integer.class,
                                networkId,
                                "_" + tpName);

                String planSql = """
                                select count(*)
                                from CS_RAT_SERVICE_PLANS
                                where NETWORK_ID = ?
                                and SERVICE_PLAN_DESC like '%' || ?
                                """;

                Integer planCount = jdbcTemplate.queryForObject(
                                planSql,
                                Integer.class,
                                networkId,
                                "_" + tpName);

                return (pkgCount != null && pkgCount > 0) ||
                                (planCount != null && planCount > 0);
        }

        // Get old plan id from old package id.
        public Long getOldPlanId(Long networkId, Long servicePackageId) {

                return jdbcTemplate.query(
                                """
                                                select SERVICE_PLAN_ID
                                                from CS_RAT_SERVICE_PLAN_PACKAGE
                                                where NETWORK_ID = ?
                                                and SERVICE_PACKAGE_ID = ?
                                                fetch first 1 rows only
                                                """,
                                rs -> rs.next() ? rs.getLong("SERVICE_PLAN_ID") : null,
                                networkId,
                                servicePackageId);
        }

        // Get old plan zone id from CS_RAT_SERVICE_PLANS.
        public Long getPlanZoneId(Long planId) {

                if (planId == null) {
                        return null;
                }

                return jdbcTemplate.query(
                                """
                                                select ZONE_GROUP_ID
                                                from CS_RAT_SERVICE_PLANS
                                                where SERVICE_PLAN_ID = ?
                                                """,
                                rs -> rs.next() ? rs.getObject("ZONE_GROUP_ID", Long.class) : null,
                                planId);
        }

        // Carries the zone id(s) and the TYPE_OF_SERVICE for a plan so the
        // caller can decide (via ServiceplanZone.resolveZoneTableByTypeOfService)
        // which zone table to clone into, instead of checking both tables.
        // dataZoneGroupId is the legacy CS_RAT_SERVICE_PLANS.DATA_ZONE_GROUP_ID
        // column — a second, independent zone reference distinct from both
        // ZONE_GROUP_ID and the CS_RAT_SERVICE_DATA_ZONE_MAP table.
        public static class PlanZoneInfo {

                private final Long zoneGroupId;
                private final Integer typeOfService;
                private final Long dataZoneGroupId;

                public PlanZoneInfo(Long zoneGroupId, Integer typeOfService, Long dataZoneGroupId) {
                        this.zoneGroupId = zoneGroupId;
                        this.typeOfService = typeOfService;
                        this.dataZoneGroupId = dataZoneGroupId;
                }

                public Long getZoneGroupId() {
                        return zoneGroupId;
                }

                public Integer getTypeOfService() {
                        return typeOfService;
                }

                public Long getDataZoneGroupId() {
                        return dataZoneGroupId;
                }
        }

        // Get old plan zone id(s) + TYPE_OF_SERVICE from CS_RAT_SERVICE_PLANS in
        // one read. Used by cloneServicePlans to resolve which zone table (RAT vs
        // DRE) the plan's zone should be cloned through.
        public PlanZoneInfo getPlanZoneInfo(Long planId) {

                if (planId == null) {
                        return new PlanZoneInfo(null, null, null);
                }

                return jdbcTemplate.query(
                                """
                                                select ZONE_GROUP_ID, TYPE_OF_SERVICE, DATA_ZONE_GROUP_ID
                                                from CS_RAT_SERVICE_PLANS
                                                where SERVICE_PLAN_ID = ?
                                                """,
                                rs -> rs.next()
                                                ? new PlanZoneInfo(
                                                                rs.getObject("ZONE_GROUP_ID", Long.class),
                                                                rs.getObject("TYPE_OF_SERVICE", Integer.class),
                                                                rs.getObject("DATA_ZONE_GROUP_ID", Long.class))
                                                : new PlanZoneInfo(null, null, null),
                                planId);
        }

        // TYPE 3 (DATA) plans don't carry their zone in
        // CS_RAT_SERVICE_PLANS.ZONE_GROUP_ID — it's looked up separately via
        // CS_RAT_SERVICE_DATA_ZONE_MAP (SERVICE_PLAN_ID -> DATA_ZONE_ID). This
        // mapping is ONE-TO-MANY: a single plan can have multiple DATA_ZONE_ID
        // rows, so every row for the plan must be fetched, not just the first.
        public List<Long> getDataZoneIds(Long planId) {

                if (planId == null) {
                        return new ArrayList<>();
                }

                return jdbcTemplate.queryForList(
                                """
                                                select DATA_ZONE_ID
                                                from CS_RAT_SERVICE_DATA_ZONE_MAP
                                                where SERVICE_PLAN_ID = ?
                                                """,
                                Long.class,
                                planId);
        }

        // Single-value convenience wrapper for callers (e.g. the top-level
        // "preview" zone resolution in TariffApprovalService/TariffPackageSyncService)
        // that only need one representative old zone id. The real, authoritative
        // clone-and-remap of EVERY mapped zone happens in cloneServicePlans below via
        // getDataZoneIds, not here.
        public Long getDataZoneId(Long planId) {

                List<Long> zoneIds = getDataZoneIds(planId);
                return zoneIds.isEmpty() ? null : zoneIds.get(0);
        }

        // Single place that decides where a plan's *old* zone id comes from:
        // TYPE 3 (DATA) -> CS_RAT_SERVICE_DATA_ZONE_MAP, everything else ->
        // CS_RAT_SERVICE_PLANS.ZONE_GROUP_ID (already in planZoneInfo).
        public Long resolveOldPlanZoneId(Long planId, PlanZoneInfo planZoneInfo) {

                boolean isDataPlan = planZoneInfo.getTypeOfService() != null
                                && planZoneInfo.getTypeOfService() == 3;

                return isDataPlan ? getDataZoneId(planId) : planZoneInfo.getZoneGroupId();
        }

        // Counterpart of resolveOldPlanZoneId for the *new* plan: TYPE 3 (DATA)
        // plans get a row in CS_RAT_SERVICE_DATA_ZONE_MAP instead of having
        // ZONE_GROUP_ID populated on CS_RAT_SERVICE_PLANS.
        private void mapDataZone(Long newPlanId, Long newZoneId) {

                try {
                        jdbcTemplate.update(
                                        """
                                                        insert into CS_RAT_SERVICE_DATA_ZONE_MAP
                                                        (
                                                            SERVICE_PLAN_ID,
                                                            DATA_ZONE_ID
                                                        )
                                                        values (?,?)
                                                        """,
                                        newPlanId,
                                        newZoneId);
                } catch (Exception ex) {
                        throw new TariffInsertException("cloneServicePlans", "CS_RAT_SERVICE_DATA_ZONE_MAP", ex);
                }
        }

        // Generate new zone id based on plan table. Delegates to ServiceplanZone
        // so there's one place (ZoneTable.getSequenceName()) that knows which
        // sequence backs which table.
        public Long generateNewPlanZoneId() {
                return servicePlanZone.generateNewZoneId(ServiceplanZone.ZoneTable.RAT_ZONE_GROUPS);
        }

        // Clone service package and service plan.

        @Transactional
        public CloneServiceResult cloneService(Long networkId,
                        Long servicePackageId,
                        String tpName,
                        Long newPlanZoneId) {

                logger.info("Clone service started networkId={} servicePackageId={} tpName={} newPlanZoneId={}",
                                networkId, servicePackageId, tpName, newPlanZoneId);

                Long newPackageId = jdbcTemplate.queryForObject(
                                """
                                     SELECT SEQ_SERVICE_PACK_ID.NEXTVAL FROM DUAL
                                                """,                          Long.class);
                                                System.out.println("=====================>"+newPackageId);

                try {
                        jdbcTemplate.update(
                                        """
                                                        insert into CS_RAT_SERVICE_PACKAGE
                                                            (
                                                                SERVICE_PACKAGE_ID,
                                                                SERVICE_PACKAGE_DESC,
                                                                RENTAL_AMOUNT,
                                                                ACTIVATION_CHARGE,
                                                                NETWORK_ID,
                                                                TAX1,
                                                                TAX2,
                                                                TAX3,
                                                                CHARGE_ID,
                                                                ADD_PACK_YN,
                                                                RENTAL_TYPE,
                                                                RENTAL_PERIOD,
                                                                ASP_TYPE,
                                                                END_DATE,
                                                                SERVICE_DURATION,
                                                                ATP_CATEGORY,
                                                                TRANSFEROR_CHARGE,
                                                                TRANSFEREE_CHARGE,
                                                                CHANGE_MSISDN_CHARGE,
                                                                MAX_AMT_PER_TRANS,
                                                                PUBLICITY_ID,
                                                                MAX_FNFSERVICE_NUMBERS,
                                                                MAX_SMSSERVICE_NUMBERS,
                                                                CA_SERVICE_PACKAGE_YN,
                                                                ATP_CATEGORY_BY_OFFER,
                                                                DESCRIPTION,
                                                                CHARGE_ON_FIRST_USAGE_YN
                                                            )
                                                            select
                                                                ?,
                                                                REGEXP_REPLACE(SERVICE_PACKAGE_DESC,'_(CL|TP|ATP)[0-9]+$','') || '_' || ?,
                                                                RENTAL_AMOUNT,
                                                                ACTIVATION_CHARGE,
                                                                NETWORK_ID,
                                                                TAX1,
                                                                TAX2,
                                                                TAX3,
                                                                CHARGE_ID,
                                                                ADD_PACK_YN,
                                                                RENTAL_TYPE,
                                                                RENTAL_PERIOD,
                                                                ASP_TYPE,
                                                                END_DATE,
                                                                SERVICE_DURATION,
                                                                ATP_CATEGORY,
                                                                TRANSFEROR_CHARGE,
                                                                TRANSFEREE_CHARGE,
                                                                CHANGE_MSISDN_CHARGE,
                                                                MAX_AMT_PER_TRANS,
                                                                REGEXP_REPLACE(PUBLICITY_ID,'_(CL|TP|ATP)[0-9]+$','') || '_' || ?,
                                                                MAX_FNFSERVICE_NUMBERS,
                                                                MAX_SMSSERVICE_NUMBERS,
                                                                CA_SERVICE_PACKAGE_YN,
                                                                ATP_CATEGORY_BY_OFFER,
                                                                DESCRIPTION,
                                                                CHARGE_ON_FIRST_USAGE_YN
                                                            from CS_RAT_SERVICE_PACKAGE
                                                            where SERVICE_PACKAGE_ID = ?
                                                                            """,
                                        newPackageId,
                                        tpName,
                                        tpName,
                                        servicePackageId);
                } catch (Exception ex) {
                        throw new TariffInsertException("cloneService", "CS_RAT_SERVICE_PACKAGE", ex);
                }

                try {
                        int count = jdbcTemplate.update(
                                        """
                                                        insert into CS_RAT_SERVICE_ATP_MAP
                                                        (
                                                            NETWORK_ID,
                                                            SERVICE_PACKAGE_ID,
                                                            BASIC_SERVICE_ID,
                                                            DERIVED_SERVICE_ID
                                                        )
                                                        select
                                                            NETWORK_ID,
                                                            ?,
                                                            BASIC_SERVICE_ID,
                                                            DERIVED_SERVICE_ID
                                                        from CS_RAT_SERVICE_ATP_MAP
                                                        where NETWORK_ID = ?
                                                        and SERVICE_PACKAGE_ID = ?
                                                        """,
                                        newPackageId,
                                        networkId,
                                        servicePackageId);

                        logger.info(
                                        "Cloned {} records from CS_RAT_SERVICE_ATP_MAP oldPackageId={} newPackageId={}",
                                        count,
                                        servicePackageId,
                                        newPackageId);

                } catch (Exception ex) {
                        throw new TariffInsertException(
                                        "cloneService",
                                        "CS_RAT_SERVICE_ATP_MAP",
                                        ex);
                }

                List<Long> newPlanIds = cloneServicePlans(networkId, servicePackageId, newPackageId, tpName);

                if (newPlanIds.isEmpty()) {
                        throw new RuntimeException("No service plan found for servicePackageId=" + servicePackageId);
                }

                Long lastNewPlanId = newPlanIds.get(newPlanIds.size() - 1);

                logger.info("Clone service completed newPackageId={} lastNewPlanId={}",
                                newPackageId, lastNewPlanId);

                return new CloneServiceResult(newPackageId, lastNewPlanId, newPlanZoneId);
        }

        /**
         * Clones every CS_RAT_SERVICE_PLANS row mapped (via CS_RAT_SERVICE_PLAN_PACKAGE)
         * to oldPackageId, and re-maps each new plan to newPackageId.
         *
         * Shared by both flows:
         *  - TP packages (called from cloneService above)
         *  - ATP packages (called from BundleService.cloneAtpData)
         *
         * Zone handling is per-plan: only when the source plan itself has a
         * ZONE_GROUP_ID do we generate + clone a new zone and map it to the
         * cloned plan. If the source plan has no zone, the cloned plan is left
         * with ZONE_GROUP_ID = null — no zone is created for it.
         */
        public List<Long> cloneServicePlans(Long networkId,
                        Long oldPackageId,
                        Long newPackageId,
                        String suffix) {

                List<Long> oldPlanIds = jdbcTemplate.queryForList(
                                """
                                                select SERVICE_PLAN_ID
                                                from CS_RAT_SERVICE_PLAN_PACKAGE
                                                where NETWORK_ID = ?
                                                and SERVICE_PACKAGE_ID = ?
                                                """,
                                Long.class,
                                networkId,
                                oldPackageId);

                if (oldPlanIds.isEmpty()) {
                        logger.info("No service plans found for packageId={}. Nothing to clone.", oldPackageId);
                        return new ArrayList<>();
                }

                List<Long> newPlanIds = new ArrayList<>();

                for (Long oldPlanId : oldPlanIds) {

                        PlanZoneInfo planZoneInfo = getPlanZoneInfo(oldPlanId);
                        boolean isDataPlan = planZoneInfo.getTypeOfService() != null
                                        && planZoneInfo.getTypeOfService() == 3;

                        // TYPE 1/2: single zone via ZONE_GROUP_ID -> RAT_ZONE_GROUPS.
                        // TYPE 3 (DATA): CS_RAT_SERVICE_DATA_ZONE_MAP is one-to-many, so
                        // every mapped zone for this plan is cloned, not just one.
                        Long newZoneIdForPlan = null;
                        List<Long> newDataZoneIds = new ArrayList<>();

                        if (isDataPlan) {

                                List<Long> oldDataZoneIds = getDataZoneIds(oldPlanId);

                                if (oldDataZoneIds.isEmpty()) {
                                        logger.info(
                                                        "Plan oldPlanId={} (TYPE 3) has no zones in CS_RAT_SERVICE_DATA_ZONE_MAP. Skipping zone creation.",
                                                        oldPlanId);
                                } else {
                                        for (Long oldDataZoneId : oldDataZoneIds) {

                                                Long newDataZoneId = servicePlanZone.generateNewZoneId(
                                                                ServiceplanZone.ZoneTable.DRE_RATING_GROUP_DETAILS);
                                                servicePlanZone.cloneZoneIfExists(oldDataZoneId, newDataZoneId, networkId, suffix,
                                                                ServiceplanZone.ZoneTable.DRE_RATING_GROUP_DETAILS);
                                                newDataZoneIds.add(newDataZoneId);

                                                logger.info(
                                                                "DATA zone cloned for plan oldPlanId={} oldDataZoneId={} newDataZoneId={}",
                                                                oldPlanId, oldDataZoneId, newDataZoneId);
                                        }
                                }

                        } else {

                                Long oldZoneId = planZoneInfo.getZoneGroupId();

                                if (oldZoneId != null) {

                                        ServiceplanZone.ZoneTable zoneTable =
                                                        servicePlanZone.resolveZoneTableByTypeOfService(planZoneInfo.getTypeOfService());

                                        newZoneIdForPlan = servicePlanZone.generateNewZoneId(zoneTable);
                                        servicePlanZone.cloneZoneIfExists(oldZoneId, newZoneIdForPlan, networkId, suffix, zoneTable);

                                        logger.info(
                                                        "Zone cloned for plan oldPlanId={} oldZoneId={} newZoneId={} typeOfService={} table={}",
                                                        oldPlanId, oldZoneId, newZoneIdForPlan,
                                                        planZoneInfo.getTypeOfService(), zoneTable);
                                } else {
                                        logger.info("Plan oldPlanId={} has no zone mapped. Skipping zone creation.", oldPlanId);
                                }
                        }

                        Long newPlanId = jdbcTemplate.queryForObject(
                                        """
                                                        SELECT SEQ_SERVICE_PLAN_ID.NEXTVAL FROM DUAL
                                                        """,
                                        Long.class);

                        // ZONE_GROUP_ID on CS_RAT_SERVICE_PLANS only applies to TYPE 1/2
                        // plans; TYPE 3 (DATA) plans are mapped separately below via
                        // CS_RAT_SERVICE_DATA_ZONE_MAP, so the column stays null for them.
                        Long zoneGroupIdColumnValue = isDataPlan ? null : newZoneIdForPlan;

                        // DATA_ZONE_GROUP_ID is a second, independent zone reference on
                        // CS_RAT_SERVICE_PLANS (separate from ZONE_GROUP_ID and from
                        // CS_RAT_SERVICE_DATA_ZONE_MAP) and always clones into
                        // CS_DRE_RATING_GROUP_DETAILS. Cloned whenever the source plan
                        // has one, independent of TYPE_OF_SERVICE.
                        Long oldDataZoneGroupId = planZoneInfo.getDataZoneGroupId();
                        Long newDataZoneGroupIdForPlan = null;

                        if (oldDataZoneGroupId != null) {

                                newDataZoneGroupIdForPlan = servicePlanZone.generateNewZoneId(
                                                ServiceplanZone.ZoneTable.DRE_RATING_GROUP_DETAILS);
                                servicePlanZone.cloneZoneIfExists(oldDataZoneGroupId, newDataZoneGroupIdForPlan, networkId,
                                                suffix, ServiceplanZone.ZoneTable.DRE_RATING_GROUP_DETAILS);

                                logger.info(
                                                "DATA_ZONE_GROUP_ID cloned for plan oldPlanId={} oldDataZoneGroupId={} newDataZoneGroupId={}",
                                                oldPlanId, oldDataZoneGroupId, newDataZoneGroupIdForPlan);
                        } else {
                                logger.info("Plan oldPlanId={} has no DATA_ZONE_GROUP_ID mapped. Skipping.", oldPlanId);
                        }

                        // Clone service plan.

                        try {
                                jdbcTemplate.update(
                                                """
                                                                insert into CS_RAT_SERVICE_PLANS
                                                                select
                                                                    NETWORK_ID,
                                                                    ?,
                                                                    REGEXP_REPLACE(SERVICE_PLAN_DESC,'_(CL|TP|ATP)[0-9]+$','') || '_' || ?,
                                                                    SERVICE_PLAN_TYPE,
                                                                    TYPE_OF_SERVICE,
                                                                    PRIORITY,
                                                                    LIMITED_HOURS_YN,
                                                                    SERVICE_PLAN_FREQ_FROM_HRS,
                                                                    SERVICE_PLAN_FREQ_TO_HRS,
                                                                    ALLOW_MTC,
                                                                    ALLOW_MOC,
                                                                    ALLOW_NLD_MO,
                                                                    ALLOW_ILD_MO,
                                                                    ALLOW_DATA,
                                                                    RATING_TYPE,
                                                                    ?,
                                                                    SMS_ZONE_GROUP_ID,
                                                                    NS_LOCAL_ONNET_CALENDAR_ID,
                                                                    NS_LOCAL_OFFNET_CALENDAR_ID,
                                                                    NS_NLD_CALENDAR_ID,
                                                                    NS_ILD_CALENDAR_ID,
                                                                    LIMITED_NETWORKS_YN,
                                                                    BNDL_WITH_SP_YN,
                                                                    BUNDLE_ID,
                                                                    SYSDATE,
                                                                    CREATED_BY,
                                                                    SMS_CALENDAR_ID,
                                                                    FNF_MAX_LINES,
                                                                    STATUS,
                                                                    FNF_MAX_GROUPS,
                                                                    GROUPS_ALLOWED,
                                                                    ?,
                                                                    ALLOW_NTNL_RM_DATA,
                                                                    ALLOW_INT_RM_DATA,
                                                                    RENTAL_DEDUCTION_IN_GRACE,
                                                                    MT_CALENDER_ID,
                                                                    DEVICE_GROUP_ID,
                                                                    MMS_CALENDAR_ID,
                                                                    PLAN_CONFIRM_NOTIFICATION,
                                                                    PLAN_EXP_NOTIFICATION,
                                                                    PLAN_EXP_NOTIF_THRESHOLD_DAYS,
                                                                    PLAN_EXP_NOTIF_THRESHOLD_HRS,
                                                                    ZONE_BASED_VIP_PLAN_FLAG_YN
                                                                from CS_RAT_SERVICE_PLANS
                                                                where SERVICE_PLAN_ID = ?
                                                                """,
                                                newPlanId,
                                                suffix,
                                                zoneGroupIdColumnValue,
                                                newDataZoneGroupIdForPlan,
                                                oldPlanId);
                        } catch (Exception ex) {
                                throw new TariffInsertException("cloneServicePlans", "CS_RAT_SERVICE_PLANS", ex);
                        }

                        try {
                                jdbcTemplate.update(
                                                """
                                                                insert into CS_RAT_SERVICE_PLAN_PACKAGE
                                                                (
                                                                    SERVICE_PACKAGE_ID,
                                                                    SERVICE_PLAN_ID,
                                                                    NETWORK_ID
                                                                )
                                                                values (?,?,?)
                                                                """,
                                                newPackageId,
                                                newPlanId,
                                                networkId);
                        } catch (Exception ex) {
                                throw new TariffInsertException("cloneServicePlans", "CS_RAT_SERVICE_PLAN_PACKAGE", ex);
                        }

                        // TYPE 3 (DATA) plans: map the new plan to EVERY newly cloned
                        // zone in CS_RAT_SERVICE_DATA_ZONE_MAP (one row per old mapping).
                        for (Long newDataZoneId : newDataZoneIds) {
                                mapDataZone(newPlanId, newDataZoneId);
                        }
                        if (!newDataZoneIds.isEmpty()) {
                                logger.info("DATA zones mapped newPlanId={} newDataZoneIds={}", newPlanId, newDataZoneIds);
                        }

                        logger.info(
                                        "Service plan cloned oldPlanId={} newPlanId={} oldPackageId={} newPackageId={} zoneId={} dataZoneGroupId={} dataZoneIds={}",
                                        oldPlanId, newPlanId, oldPackageId, newPackageId, newZoneIdForPlan, newDataZoneGroupIdForPlan,
                                        newDataZoneIds);

                        newPlanIds.add(newPlanId);
                }

                return newPlanIds;
        }
}