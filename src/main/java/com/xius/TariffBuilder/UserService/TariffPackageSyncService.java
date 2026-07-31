package com.xius.TariffBuilder.UserService;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import com.xius.TariffBuilder.UserService.BundleService.CloneAtpResult;
import com.xius.TariffBuilder.UserService.ServiceCloneService.CloneServiceResult;
import com.xius.TariffBuilder.exception.TariffInsertException;
import com.xius.TariffBuilder.UserService.HlrCodeMappingService;

@Service
public class TariffPackageSyncService {

    private static final Logger logger = LoggerFactory.getLogger(TariffPackageSyncService.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    // ATP_CATEGORY value (CS_RAT_SERVICE_PACKAGE) that routes an ATP through
    // the CA Package clone flow instead of Bundle/Bucket — same constant
    // BundleService uses for the clone/approve side, kept in sync here so the
    // update/sync flow branches the same way.
    private static final String ATP_CATEGORY_CA = "CA";

    @Qualifier("oracleJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    private final PlatformTransactionManager transactionManager;
    private final ServiceCloneService serviceCloneService;
    private final BundleService bundleService;
    private final ServiceplanZone servicePlanZone;
    private final RcAtpRechargeService rcAtpRechargeService;
    private final SeriesGeneratorService seriesGeneratorService;
    private final CaPackageService caPackageService;
    private final HlrCodeMappingService hlrCodeMappingService;

    TariffPackageSyncService(JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            ServiceCloneService serviceCloneService,
            BundleService bundleService,
            ServiceplanZone servicePlanZone,
            RcAtpRechargeService rcAtpRechargeService,
            SeriesGeneratorService seriesGeneratorService,
            CaPackageService caPackageService,HlrCodeMappingService hlrCodeMappingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
        this.serviceCloneService = serviceCloneService;
        this.bundleService = bundleService;
        this.servicePlanZone = servicePlanZone;
        this.rcAtpRechargeService = rcAtpRechargeService;
        this.seriesGeneratorService = seriesGeneratorService;
        this.caPackageService = caPackageService;
        this.hlrCodeMappingService = hlrCodeMappingService;
    }

    // =====================================================================
    // ENTRY POINT
    // =====================================================================
    @SuppressWarnings("unchecked")
    public Map<String, Object> syncTariffPackage(Long tariffPackageId, Long networkId, Map<String, Object> requestBody,
            String username) {

        logger.info("SyncTariffPackage started tariffPackageId={} networkId={}", tariffPackageId, networkId);

        Map<String, Object> data = (Map<String, Object>) requestBody.get("data");
        if (data == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "error");
            err.put("message", "Request body must contain a 'data' object");
            return err;
        }

        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = transactionManager.getTransaction(def);
        Date startDate = Date.valueOf(
                LocalDate.parse(data.get("startDate").toString(), formatter));

        Date endDate = Date.valueOf(
                LocalDate.parse(data.get("endDate").toString(), formatter));

        try {
            // ── 1. Direct attribute update on CS_RAT_TARIFF_PACKAGE / TPID_VS_PUBLICITYID
            // ──
            updateTariffPackageAttributes(tariffPackageId, networkId, data);

            // ── 2. Load current DB mapping state for this Tariff Package ──────
            List<Map<String, Object>> existingMappings = jdbcTemplate.queryForList("""
                    SELECT SERVICE_PACKAGE_ID, TARIFF_PLAN_TYPE, CHARGE_ID
                    FROM CS_RAT_TARIFF_SERVICE_PACK_MAP
                    WHERE TARIFF_PACKAGE_ID = ?
                      AND NETWORK_ID = ?
                    """, tariffPackageId, networkId);

            Map<Long, String> existingTp = new LinkedHashMap<>();
            Map<Long, String> existingDatp = new LinkedHashMap<>();
            Map<Long, String> existingRcAtp = new LinkedHashMap<>();

            for (Map<String, Object> row : existingMappings) {
                Long spId = ((Number) row.get("SERVICE_PACKAGE_ID")).longValue();
                String chargeId = row.get("CHARGE_ID") != null ? row.get("CHARGE_ID").toString() : null;
                String type = String.valueOf(row.get("TARIFF_PLAN_TYPE"));
                switch (type) {
                    case "TP" -> existingTp.put(spId, chargeId);
                    case "DATP" -> existingDatp.put(spId, chargeId);
                    case "RCATP" -> existingRcAtp.put(spId, chargeId);
                    default -> logger.warn("Unknown TARIFF_PLAN_TYPE={} spId={} skipped", type, spId);
                }
            }

            // ── 3. Read desired state from request ─────────────────────────────
            List<Map<String, Object>> incomingTp = new ArrayList<>();
            Object tpPlanIdRaw = data.get("tariffPlanId");
            if (tpPlanIdRaw != null) {
                Map<String, Object> tpEntry = new LinkedHashMap<>();
                tpEntry.put("servicePackageId", tpPlanIdRaw);
                incomingTp.add(tpEntry);
            }

            List<Map<String, Object>> incomingDatp = (List<Map<String, Object>>) data.getOrDefault("defaultAtps",
                    List.of());
            List<Map<String, Object>> incomingRcAtp = (List<Map<String, Object>>) data.getOrDefault("allowedAtps",
                    List.of());

            // ── 4. Sync each plan type ──────────────────────────────────────────
            syncTp(tariffPackageId, networkId, username, incomingTp, existingTp, startDate, endDate);
            syncDatp(tariffPackageId, networkId, username, incomingDatp, existingDatp, data, startDate, endDate);
            syncRcAtp(tariffPackageId, networkId, username, incomingRcAtp, existingRcAtp, data, startDate, endDate);

            transactionManager.commit(status);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "success");
            result.put("message", "Tariff package synced successfully");
            result.put("tariffPackageId", tariffPackageId);
            logger.info("SyncTariffPackage committed tariffPackageId={}", tariffPackageId);
            return result;

        } catch (TariffInsertException tie) {
            transactionManager.rollback(status);
            logger.error("SyncTariffPackage failed (insert) tariffPackageId={} step={} table={}", tariffPackageId,
                    tie.getStep(), tie.getFailedTable(), tie);
            throw tie;
        } catch (Exception ex) {
            transactionManager.rollback(status);
            logger.error("SyncTariffPackage failed tariffPackageId={} error={}", tariffPackageId, ex.getMessage(), ex);
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(ex);
        }
    }

    // =====================================================================
    // STEP 1 — direct field update
    // =====================================================================
    private void updateTariffPackageAttributes(Long tariffPackageId, Long networkId, Map<String, Object> data) {

        try {
            jdbcTemplate.update("""
                    UPDATE CS_RAT_TARIFF_PACKAGE SET
                        TARIFF_PACKAGE_DESC  = ?,
                        PUBLICITY_ID         = ?,
                        PACKAGE_TYPE         = ?,
                        IS_CORPORATE_YN      = ?,
                        TARIFF_PACK_CATEGORY = ?,
                        END_DATE             = ?
                    WHERE TARIFF_PACKAGE_ID = ?
                      AND NETWORK_ID = ?
                    """,
                    data.get("tariffPackageDesc"),
                    data.get("publicityId"),
                    data.get("packageType"),
                    convertYN(data.get("isCorporateYn")),
                    data.get("tariffPackCategory"),
                    Date.valueOf(LocalDate.parse(data.get("endDate").toString(), formatter)),
                    tariffPackageId,
                    networkId);
        } catch (Exception ex) {
            throw new TariffInsertException("UPDATE_TP", "CS_RAT_TARIFF_PACKAGE", ex);
        }

        List<Map<String, Object>> defaultAtps = (List<Map<String, Object>>) data.get("defaultAtps");
        List<Map<String, Object>> addAtps = (List<Map<String, Object>>) data.get("allowedAtps");
        List<Long> defaultAtpIds = new ArrayList<>();
        List<Long> allowedAtpIds = new ArrayList<>();

        // These lists drive the PRIORITY update loops below — without populating them
        // here, the loops silently ran zero times and PRIORITY was never updated.
        if (defaultAtps != null) {
            for (Map<String, Object> atp : defaultAtps) {
                defaultAtpIds.add(Long.valueOf(atp.get("servicePackageId").toString()));
            }
        }
        if (addAtps != null) {
            for (Map<String, Object> atp : addAtps) {
                allowedAtpIds.add(Long.valueOf(atp.get("servicePackageId").toString()));
            }
        }

        int datpIdx = 0;
        for (Long atpId : defaultAtpIds) {

            Object priorityObj = defaultAtps.get(datpIdx).get("priority");
            String priorityStr = priorityObj != null ? priorityObj.toString().trim() : "";
            Integer priorityValue = priorityStr.isEmpty() ? 0 : Integer.valueOf(priorityStr);

            String atpChargeId = defaultAtps.get(datpIdx).get("chargeId").toString();

            jdbcTemplate.update("""
                    UPDATE CS_RAT_TARIFF_SERVICE_PACK_MAP
                       SET PRIORITY = ?,
                           CHARGE_ID = ?
                     WHERE TARIFF_PACKAGE_ID = ?
                       AND SERVICE_PACKAGE_ID = ?
                       AND NETWORK_ID = ?
                       AND TARIFF_PLAN_TYPE = 'DATP'
                    """,
                    priorityValue,
                    atpChargeId,
                    tariffPackageId,
                    atpId,
                    networkId);

            datpIdx++;
        }

        int aatpIdx = 0;
        for (Long atpId : allowedAtpIds) {

            Object priorityObj = addAtps.get(aatpIdx).get("priority");
            String priorityStr = priorityObj != null ? priorityObj.toString().trim() : "";
            Integer priorityValue = priorityStr.isEmpty() ? 0 : Integer.valueOf(priorityStr);

            String atpChargeId = addAtps.get(aatpIdx).get("chargeId").toString();

            jdbcTemplate.update("""
                    UPDATE CS_RAT_TARIFF_SERVICE_PACK_MAP
                       SET PRIORITY = ?,
                           CHARGE_ID = ?
                     WHERE TARIFF_PACKAGE_ID = ?
                       AND SERVICE_PACKAGE_ID = ?
                       AND NETWORK_ID = ?
                       AND TARIFF_PLAN_TYPE = 'RCATP'
                    """,
                    priorityValue,
                    atpChargeId,
                    tariffPackageId,
                    atpId,
                    networkId);

            aatpIdx++;
        }

        int rows = jdbcTemplate.update("""
                UPDATE CS_RAT_PERIODIC_CHARGE_INFO
                   SET ACTIVATION_FEE = ?
                 WHERE CHARGE_ID = ?
                   AND NETWORK_ID = ?
                """,
                data.get("charge"),
                data.get("periodicChargeID"),
                networkId);

        logger.info(
                "TP activation fee updated chargeId={} rowsAffected={}",
                data.get("periodicChargeID"),
                rows);

        try {
            jdbcTemplate.update("""
                    UPDATE CS_RAT_TPID_VS_PUBLICITYID SET
                        TARIFF_PACKAGE_DESC = ?,
                        PUBLICITY_ID        = ?
                    WHERE TARIFF_PACKAGE_ID = ?
                      AND NETWORK_ID = ?
                    """,
                    data.get("tariffPackageDesc"),
                    data.get("publicityId"),
                    tariffPackageId,
                    networkId);
        } catch (Exception ex) {
            throw new TariffInsertException("UPDATE_TP", "CS_RAT_TPID_VS_PUBLICITYID", ex);
        }
        rcAtpRechargeService.updateRcNamesByTariffPackage(
                tariffPackageId,
                networkId,
                String.valueOf(data.get("publicityId")));

        logger.info("Direct attributes updated tariffPackageId={}", tariffPackageId);
    }

    // TP SYNC (base plan — normally a single row)
    private void syncTp(Long tariffPackageId, Long networkId, String username,
            List<Map<String, Object>> incomingTp, Map<Long, String> existingTp, Date startDate, Date endDate) {

        Set<Long> incomingIds = new LinkedHashSet<>();
        for (Map<String, Object> entry : incomingTp) {
            incomingIds.add(Long.valueOf(entry.get("servicePackageId").toString()));
        }

        for (Long id : existingTp.keySet()) {
            if (incomingIds.contains(id)) {
                logger.info("TP unchanged (kept as-is), servicePackageId={}", id);
            }
        }

        // New TP added (id not currently mapped for this Tariff Package)
        for (Map<String, Object> entry : incomingTp) {
            Long id = Long.valueOf(entry.get("servicePackageId").toString());
            if (!existingTp.containsKey(id)) {
                logger.info("New TP detected, cloning from catalog sourceId={}", id);
                addNewTp(tariffPackageId, networkId, id, startDate, endDate);
            }
        }

        // TP removed (existing id no longer present in request)
        for (Long id : existingTp.keySet()) {
            if (!incomingIds.contains(id)) {
                logger.info("TP removed, unmapping servicePackageId={}", id);
                removeMapping(tariffPackageId, networkId, id, "TP");
                if (!isServicePackageReferencedElsewhere(id, networkId, tariffPackageId)) {
                    deleteServicePackageHierarchy(id, networkId);
                }
            }
        }
    }

    private void addNewTp(Long tariffPackageId, Long networkId, Long sourceServicePackageId, Date startDate,
            Date endDate) {

        Long oldPlanId = serviceCloneService.getOldPlanId(networkId, sourceServicePackageId);
        ServiceCloneService.PlanZoneInfo oldPlanZoneInfo = serviceCloneService.getPlanZoneInfo(oldPlanId);
        Long oldPlanZoneId = serviceCloneService.resolveOldPlanZoneId(oldPlanId, oldPlanZoneInfo);
        ServiceplanZone.ZoneTable planZoneTable = servicePlanZone
                .resolveZoneTableByTypeOfService(oldPlanZoneInfo.getTypeOfService());
        Long newPlanZoneId = servicePlanZone.generateNewZoneId(planZoneTable);
        String suffix = "TP" + seriesGeneratorService.resolveNextTpSuffixNumber();
        servicePlanZone.cloneZoneIfExists(oldPlanZoneId, newPlanZoneId, networkId, suffix, planZoneTable);
        CloneServiceResult cloneResult = serviceCloneService.cloneService(networkId, sourceServicePackageId, suffix,
                newPlanZoneId, startDate, endDate);

        String existingChargeId = jdbcTemplate.queryForObject("""
                SELECT CHARGE_ID FROM CS_RAT_TARIFF_PACKAGE WHERE TARIFF_PACKAGE_ID = ? AND NETWORK_ID = ?  """,
                String.class, tariffPackageId, networkId);

        try {
            jdbcTemplate.update("""
                    INSERT INTO CS_RAT_TARIFF_SERVICE_PACK_MAP
                    (TARIFF_PACKAGE_ID, SERVICE_PACKAGE_ID, NETWORK_ID, TARIFF_PLAN_TYPE, CHARGE_ID)
                    VALUES (?,?,?,?,?)
                    """, tariffPackageId, cloneResult.getNewPackageId(), networkId, "TP", existingChargeId);
        } catch (Exception ex) {
            throw new TariffInsertException("ADD_TP", "CS_RAT_TARIFF_SERVICE_PACK_MAP(TP)", ex);
        }

        logger.info("New TP mapped tariffPackageId={} newServicePackageId={}", tariffPackageId,
                cloneResult.getNewPackageId());
    }

    // DATP SYNC
    private void syncDatp(Long tariffPackageId, Long networkId, String username,
            List<Map<String, Object>> incomingDatp, Map<Long, String> existingDatp, Map<String, Object> data,
            Date startDate, Date endDate) {

        Set<Long> incomingIds = new LinkedHashSet<>();

        for (Map<String, Object> atp : incomingDatp) {
            if (atp.get("servicePackageId") == null) {
                continue;
            }
            Long id = Long.valueOf(atp.get("servicePackageId").toString());

            String incomingChargeId = asTrimmedStringOrNull(atp.get("chargeId"));

            if (incomingChargeId != null) {
                // ── Existing DATP modified: update in place, same SERVICE_PACKAGE_ID ──
                incomingIds.add(id);
                if (!existingDatp.containsKey(id)) {
                    logger.warn("DATP chargeId={} claims servicePackageId={} not currently mapped to "
                            + "tariffPackageId={} — updating by chargeId anyway", incomingChargeId, id,
                            tariffPackageId);
                }
                updateAtpPeriodicCharge(incomingChargeId, networkId, atp, data);

                if (caPackageService.hasCaPackage(id)) {
                    Map<String, Object> caAtpRequest = findCaAtpRequest(data, id);
                    if (caAtpRequest != null) {
                        caPackageService.updateCaPackage(id, networkId, caAtpRequest);
                        logger.info("CA Package fields updated servicePackageId={}", id);
                    } else {
                        logger.info(
                                "servicePackageId={} has a CA Package but no matching caAtps entry in this request — fields left unchanged",
                                id);
                    }
                }

                logger.info("DATP updated in place servicePackageId={} chargeId={}", id, incomingChargeId);
            } else {

                Long newAtpId = addNewAtp(tariffPackageId, networkId, username, id, "DATP", atp, data, startDate,
                        endDate);
                incomingIds.add(newAtpId);
            }
        }

        // ── DATP removed: present in DB, absent from request ──────────────────
        for (Long id : existingDatp.keySet()) {
            if (!incomingIds.contains(id)) {
                removeMapping(tariffPackageId, networkId, id, "DATP");
                if (!isServicePackageReferencedElsewhere(id, networkId, tariffPackageId)) {
                    deleteAtpHierarchy(id, networkId, existingDatp.get(id));
                } else {
                    logger.info("DATP {} referenced elsewhere — mapping removed, hierarchy kept", id);
                }
            }
        }
    }

    // RCATP SYNC
    private void syncRcAtp(Long tariffPackageId, Long networkId, String username,
            List<Map<String, Object>> incomingRcAtp, Map<Long, String> existingRcAtp, Map<String, Object> data,
            Date startDate, Date endDate) {

        Set<Long> incomingIds = new LinkedHashSet<>();

        for (Map<String, Object> atp : incomingRcAtp) {
            if (atp.get("servicePackageId") == null) {
                continue;
            }
            Long id = Long.valueOf(atp.get("servicePackageId").toString());
            String incomingChargeId = asTrimmedStringOrNull(atp.get("chargeId"));
            Double mrp = toDouble(atp.get("mrp"));

            if (incomingChargeId != null) {

                incomingIds.add(id);
                if (!existingRcAtp.containsKey(id)) {
                    logger.warn("RCATP chargeId={} claims servicePackageId={} not currently mapped to "
                            + "tariffPackageId={} — updating by chargeId anyway", incomingChargeId, id,
                            tariffPackageId);
                }
                updateAtpPeriodicCharge(incomingChargeId, networkId, atp, data);

                Object serviceCode = atp.get("serviceCode");
                if (serviceCode != null && !serviceCode.toString().trim().isEmpty()) {
                    hlrCodeMappingService.updateHlrCodeMapping(
                            networkId,
                            id,
                            Long.valueOf(serviceCode.toString()));
                }

                if (caPackageService.hasCaPackage(id)) {
                    Map<String, Object> caAtpRequest = findCaAtpRequest(data, id);
                    if (caAtpRequest != null) {
                        caPackageService.updateCaPackage(id, networkId, caAtpRequest);
                        logger.info("CA Package fields updated servicePackageId={}", id);
                    } else {
                        logger.info(
                                "servicePackageId={} has a CA Package but no matching caAtps entry in this request — fields left unchanged",
                                id);
                    }
                }

                // Keep the RC's MRP (LOW_VALUE/HIGH_VALUE) in sync with the UI edit.
                Long existingRcIdForAtp = rcAtpRechargeService.findRcIdByAtpId(id, networkId);
                if (existingRcIdForAtp != null) {
                    rcAtpRechargeService.updateRcMrp(existingRcIdForAtp, mrp, networkId);
                } else {
                    logger.warn("No RC found for existing RCATP servicePackageId={} — MRP not updated", id);
                }

                logger.info("RCATP updated in place servicePackageId={} chargeId={} mrp={}", id, incomingChargeId,
                        mrp);
            } else {

                Long newAtpId = addNewAtp(tariffPackageId, networkId, username, id, "RCATP", atp, data, startDate,
                        endDate);
                Object serviceCode = atp.get("serviceCode");
                if (serviceCode != null && !serviceCode.toString().trim().isEmpty()) {
                    hlrCodeMappingService.insertHlrCodeMapping(
                            networkId,
                            newAtpId,
                            Long.valueOf(serviceCode.toString()));
                }
                incomingIds.add(newAtpId);

                // Every RCATP gets its own brand-new RC, with a unique RC_CODE series
                // and this RCATP's MRP.
                rcAtpRechargeService.createSingleRc(tariffPackageId, String.valueOf(data.get("tariffPackageDesc")),
                        networkId, newAtpId, mrp, String.valueOf(atp.get("type")), startDate, endDate);

                logger.info("New RCATP added and RC created servicePackageId={} mrp={}", newAtpId, mrp);
            }
        }

        // ── RCATP removed ───────────────────────────────────────────────────
        for (Long id : existingRcAtp.keySet()) {
            if (!incomingIds.contains(id)) {
                removeMapping(tariffPackageId, networkId, id, "RCATP");

                hlrCodeMappingService.deleteHlrCodeMapping(networkId, id);

                Long rcIdForRemovedAtp = rcAtpRechargeService.findRcIdByAtpId(id, networkId);
                if (rcIdForRemovedAtp != null) {
                    rcAtpRechargeService.deleteRc(rcIdForRemovedAtp, networkId);
                }

                if (!isServicePackageReferencedElsewhere(id, networkId, tariffPackageId)) {
                    deleteAtpHierarchy(id, networkId, existingRcAtp.get(id));
                } else {
                    logger.info("RCATP {} referenced elsewhere — mapping removed, hierarchy kept", id);
                }
            }
        }
    }

    private Long addNewAtp(Long tariffPackageId, Long networkId, String username, Long sourceAtpId,
            String tariffPlanType, Map<String, Object> atp, Map<String, Object> data, Date startDate, Date endDate) {

        String suffix = "ATP" + seriesGeneratorService.resolveNextAtpSuffixNumber();

        boolean isCaAtp = isCaAtp(sourceAtpId, networkId);

        Long newBucketZoneId = null;

        if (!isCaAtp) {
            String oldBucketId = bundleService.getOldBucketId(sourceAtpId, networkId);
            Long oldBucketZoneId = bundleService.getBucketZoneId(oldBucketId);
            ServiceplanZone.ZoneTable bucketZoneTable = servicePlanZone.resolveZoneTableByBalanceCategory(
                    bundleService.getBucketBalanceCategory(oldBucketId));
            newBucketZoneId = servicePlanZone.generateNewZoneId(bucketZoneTable);

            servicePlanZone.cloneZoneIfExists(oldBucketZoneId, newBucketZoneId, networkId, suffix, bucketZoneTable);
        } else {
            logger.info("CA ATP detected (ATP_CATEGORY=CA) sourceAtpId={} — skipping bucket zone resolution.",
                    sourceAtpId);
        }

        Map<String, Object> caAtpRequest = isCaAtp ? findCaAtpRequest(data, sourceAtpId) : null;

        CloneAtpResult atpResult = bundleService.cloneAtpData(sourceAtpId, networkId, suffix, newBucketZoneId,
                startDate, endDate, caAtpRequest);
        Long newAtpId = atpResult.getNewAtpId();

        String chargeId = data.get("tariffPackageDesc") + "_" + suffix;
        atp.put("chargeId", chargeId);
        insertAtpPeriodicCharge(chargeId, networkId, atp, data, username);

        // CA ATPs map into CS_RAT_TARIFF_SERVICE_PACK_MAP exactly like any
        // other ATP — the CA Package clone above only replaces what
        // Bundle/Bucket would have done for a non-CA ATP.
        Object priorityObj = atp.get("priority");
        String priorityStr = priorityObj != null ? priorityObj.toString().trim() : "";
        Integer priorityValue = priorityStr.isEmpty() ? 0 : Integer.valueOf(priorityStr);

        try {
            jdbcTemplate.update("""
                    INSERT INTO CS_RAT_TARIFF_SERVICE_PACK_MAP
                    (
                        TARIFF_PACKAGE_ID,
                        SERVICE_PACKAGE_ID,
                        NETWORK_ID,
                        TARIFF_PLAN_TYPE,
                        CHARGE_ID,
                        PRIORITY,
                        SERVICE_DURATION,
                        EFFECTIVE_START_OFFSET
                    )
                    VALUES (?,?,?,?,?,?,?,?)
                    """,
                    tariffPackageId,
                    newAtpId,
                    networkId,
                    tariffPlanType,
                    chargeId,
                    priorityValue,
                    30,
                    0);
        } catch (Exception ex) {
            throw new TariffInsertException("ADD_" + tariffPlanType,
                    "CS_RAT_TARIFF_SERVICE_PACK_MAP(" + tariffPlanType + ")", ex);
        }

        logger.info("New {} mapped tariffPackageId={} sourceAtpId={} newAtpId={}", tariffPlanType, tariffPackageId,
                sourceAtpId, newAtpId);

        return newAtpId;
    }

    /** Reads ATP_CATEGORY off CS_RAT_SERVICE_PACKAGE for the given ATP. */
    private boolean isCaAtp(Long servicePackageId, Long networkId) {

        String atpCategory = jdbcTemplate.query("""
                SELECT ATP_CATEGORY
                FROM CS_RAT_SERVICE_PACKAGE
                WHERE SERVICE_PACKAGE_ID = ?
                  AND NETWORK_ID = ?
                """, rs -> rs.next() ? rs.getString("ATP_CATEGORY") : null, servicePackageId, networkId);

        return ATP_CATEGORY_CA.equals(atpCategory);
    }

    /**
     * Finds the request body's "caAtps" entry whose servicePackageId matches
     * the given source ATP id — the fresh CA Package creation fields for
     * that ATP (see CaPackageService.createCaPackage). Returns null if
     * "caAtps" is absent or has no matching entry, in which case the caller
     * falls back to cloning whatever CA Package the source ATP already had.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> findCaAtpRequest(Map<String, Object> data, Long sourceAtpId) {

        List<Map<String, Object>> caAtps = (List<Map<String, Object>>) data.get("caAtps");

        if (caAtps == null) {
            return null;
        }

        for (Map<String, Object> caAtp : caAtps) {
            Object servicePackageId = caAtp.get("servicePackageId");
            if (servicePackageId != null && sourceAtpId.equals(Long.valueOf(servicePackageId.toString()))) {
                return caAtp;
            }
        }

        return null;
    }

    private void insertAtpPeriodicCharge(String chargeId, Long networkId, Map<String, Object> atp,
            Map<String, Object> data, String username) {

        Object rentalPeriod = resolveRentalPeriod(atp);

        try {
            jdbcTemplate.update("""
                    INSERT INTO CS_RAT_PERIODIC_CHARGE_INFO
                    (CHARGE_ID, CHARGE_DESC, NETWORK_ID, RENTAL_TYPE, RENTAL_PERIOD, ACTIVATION_FEE,
                     RENTAL_FEE, RENTAL_FREE_CYCLES, AUTO_RENEWAL, PLAN_EXP_MIDNIGHT_YN, MAX_RENEWAL_COUNT,
                     CREATED_BY)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    chargeId, chargeId, networkId, atp.get("validity"), rentalPeriod,
                    atp.getOrDefault("activationFee", data.get("charge")), atp.get("rental"), atp.get("freeCycles"),
                    convertYN(atp.get("renewal")), convertYN(atp.get("midnightExpiry")), atp.get("maxCount"),
                    username);
        } catch (Exception ex) {
            throw new TariffInsertException("ADD_ATP_CHARGE", "CS_RAT_PERIODIC_CHARGE_INFO", ex);
        }
    }

    private void updateAtpPeriodicCharge(String chargeId, Long networkId, Map<String, Object> atp,
            Map<String, Object> data) {

        Object rentalPeriod = resolveRentalPeriod(atp);

        try {
            jdbcTemplate.update("""
                    UPDATE CS_RAT_PERIODIC_CHARGE_INFO SET
                        RENTAL_TYPE          = ?,
                        RENTAL_PERIOD        = ?,
                        ACTIVATION_FEE       = ?,
                        RENTAL_FEE           = ?,
                        RENTAL_FREE_CYCLES   = ?,
                        AUTO_RENEWAL         = ?,
                        PLAN_EXP_MIDNIGHT_YN = ?,
                        MAX_RENEWAL_COUNT    = ?
                    WHERE CHARGE_ID  = ?
                      AND NETWORK_ID = ?
                    """,
                    atp.get("validity"), rentalPeriod, atp.getOrDefault("activationFee", data.get("charge")),
                    atp.get("rental"), atp.get("freeCycles"), convertYN(atp.get("renewal")),
                    convertYN(atp.get("midnightExpiry")), atp.get("maxCount"), chargeId, networkId);
        } catch (Exception ex) {
            throw new TariffInsertException("UPDATE_ATP_CHARGE", "CS_RAT_PERIODIC_CHARGE_INFO", ex);
        }
    }

    private Object resolveRentalPeriod(Map<String, Object> atp) {
        Object validityDaysRaw = atp.get("rentalPeriod");
        if ("O".equals(atp.get("validity")) && validityDaysRaw != null
                && !validityDaysRaw.toString().trim().isEmpty()) {
            try {
                return Integer.parseInt(validityDaysRaw.toString().trim());
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        return 1;
    }

    private void removeMapping(Long tariffPackageId, Long networkId, Long servicePackageId, String tariffPlanType) {
        try {
            jdbcTemplate.update("""
                    DELETE FROM CS_RAT_TARIFF_SERVICE_PACK_MAP
                    WHERE TARIFF_PACKAGE_ID = ?
                      AND SERVICE_PACKAGE_ID = ?
                      AND NETWORK_ID = ?
                      AND TARIFF_PLAN_TYPE = ?
                    """, tariffPackageId, servicePackageId, networkId, tariffPlanType);
        } catch (Exception ex) {
            throw new TariffInsertException("REMOVE_" + tariffPlanType, "CS_RAT_TARIFF_SERVICE_PACK_MAP", ex);
        }
        logger.info("Mapping removed tariffPackageId={} servicePackageId={} type={}", tariffPackageId,
                servicePackageId, tariffPlanType);
    }

    private boolean isServicePackageReferencedElsewhere(Long servicePackageId, Long networkId,
            Long excludeTariffPackageId) {

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM CS_RAT_TARIFF_SERVICE_PACK_MAP
                WHERE SERVICE_PACKAGE_ID = ?
                  AND NETWORK_ID = ?
                  AND TARIFF_PACKAGE_ID <> ?
                """, Integer.class, servicePackageId, networkId, excludeTariffPackageId);

        return count != null && count > 0;
    }

    /** Deletes ATP + Bundle + Bucket hierarchy plus its periodic charge. */
    private void deleteAtpHierarchy(Long atpId, Long networkId, String chargeId) {

        // Resolved before CS_RAT_SERVICE_PACKAGE is deleted below — ATP_CATEGORY
        // wouldn't be readable afterwards. A CA ATP has no Bundle/Bucket rows
        // (the loop below simply runs zero times for it), but it does have a
        // cloned CA Package that this same method previously had no idea
        // existed, leaving it permanently orphaned in the DB.
        boolean isCaAtp = caPackageService.hasCaPackage(atpId);

        try {
            List<Long> bundleIds = jdbcTemplate.queryForList("""
                    SELECT BUNDLE_OR_DISCOUNT_ID FROM CS_ATP_ACCUMU_BON_DISC_MAP
                    WHERE ATP_ID = ? AND NETWORK_ID = ?
                    """, Long.class, atpId, networkId);

            for (Long bundleId : bundleIds) {

                List<String> bucketIds = jdbcTemplate.queryForList("""
                        SELECT BUCKET_ID FROM CS_BNDL_MT_BNDL_BUCKET_MAP WHERE BUNDLE_ID = ?
                        """, String.class, bundleId);

                jdbcTemplate.update("DELETE FROM CS_BNDL_MT_BNDL_BUCKET_MAP WHERE BUNDLE_ID = ?", bundleId);

                for (String bucketId : bucketIds) {
                    jdbcTemplate.update("DELETE FROM BNDL_MT_BUCKETS WHERE BUCKET_ID = ?", bucketId);
                }

                jdbcTemplate.update("DELETE FROM BNDL_MT_SIM_IMSI_RANGES WHERE BUNDLE_ID = ?", bundleId);
                jdbcTemplate.update("DELETE FROM BNDL_MT_BUNDLE WHERE BUNDLE_ID = ?", bundleId);
            }

            jdbcTemplate.update("DELETE FROM CS_ATP_ACCUMU_BON_DISC_MAP WHERE ATP_ID = ? AND NETWORK_ID = ?", atpId,
                    networkId);
            jdbcTemplate.update("DELETE FROM CS_RAT_SERVICE_ATP_MAP WHERE SERVICE_PACKAGE_ID = ? AND NETWORK_ID = ?",
                    atpId, networkId);

            if (chargeId != null) {
                jdbcTemplate.update("DELETE FROM CS_RAT_PERIODIC_CHARGE_INFO WHERE CHARGE_ID = ? AND NETWORK_ID = ?",
                        chargeId, networkId);
            }

            if (isCaAtp) {
                caPackageService.deleteCaPackage(atpId, networkId);
            }

            jdbcTemplate.update("DELETE FROM CS_RAT_SERVICE_PACKAGE WHERE SERVICE_PACKAGE_ID = ? AND NETWORK_ID = ?",
                    atpId, networkId);
        } catch (Exception ex) {
            // e.g. ORA-02292: another record still references CS_RAT_SERVICE_PACKAGE.
            // Wrapped so the raw SQL/Oracle message never reaches the UI — only the log.
            throw new TariffInsertException("DELETE_ATP_HIERARCHY", "CS_RAT_SERVICE_PACKAGE", ex);
        }

        logger.info("ATP hierarchy deleted atpId={} networkId={} wasCaAtp={}", atpId, networkId, isCaAtp);
    }

    /** Deletes a TP's service package + plan + plan-package mapping. */
    private void deleteServicePackageHierarchy(Long servicePackageId, Long networkId) {

        try {
            List<Long> planIds = jdbcTemplate.queryForList("""
                    SELECT SERVICE_PLAN_ID
                    FROM CS_RAT_SERVICE_PLAN_PACKAGE
                    WHERE SERVICE_PACKAGE_ID = ?
                    AND NETWORK_ID = ?
                    """,
                    Long.class,
                    servicePackageId,
                    networkId);

            // Delete ATP mappings first
            jdbcTemplate.update("""
                    DELETE FROM CS_RAT_SERVICE_ATP_MAP
                    WHERE SERVICE_PACKAGE_ID = ?
                    AND NETWORK_ID = ?
                    """,
                    servicePackageId,
                    networkId);

            jdbcTemplate.update("""
                    DELETE FROM CS_RAT_SERVICE_PLAN_PACKAGE
                    WHERE SERVICE_PACKAGE_ID = ?
                    AND NETWORK_ID = ?
                    """,
                    servicePackageId,
                    networkId);

            for (Long planId : planIds) {
                jdbcTemplate.update("""
                        DELETE FROM CS_RAT_SERVICE_PLANS
                        WHERE SERVICE_PLAN_ID = ?
                        """,
                        planId);
            }

            jdbcTemplate.update("""
                    DELETE FROM CS_RAT_SERVICE_PACKAGE
                    WHERE SERVICE_PACKAGE_ID = ?
                    AND NETWORK_ID = ?
                    """,
                    servicePackageId,
                    networkId);
        } catch (Exception ex) {
            throw new TariffInsertException("DELETE_TP_HIERARCHY", "CS_RAT_SERVICE_PACKAGE", ex);
        }

        logger.info(
                "TP service package hierarchy deleted servicePackageId={} networkId={}",
                servicePackageId,
                networkId);
    }

    /** Returns a trimmed non-empty chargeId, or null if absent/blank. */
    private String asTrimmedStringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private String convertYN(Object value) {
        if (value == null)
            return "N";
        String v = value.toString();
        if (v.equalsIgnoreCase("Y") || v.equalsIgnoreCase("YES") || v.equalsIgnoreCase("TRUE"))
            return "Y";
        return "N";
    }

    /**
     * Parses a Number/String MRP value from the request payload, or null if
     * absent/invalid.
     */
    private Double toDouble(Object value) {
        if (value == null)
            return null;
        if (value instanceof Number n)
            return n.doubleValue();
        String s = value.toString().trim();
        if (s.isEmpty())
            return null;
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}