package com.xius.TariffBuilder.UserService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import com.xius.TariffBuilder.exception.TariffInsertException;

@Service
@RequiredArgsConstructor
@Transactional
public class CaPackageService {

    private static final Logger logger = LoggerFactory.getLogger(CaPackageService.class);

    // CA_MT_PACKAGE.PACKAGE_STATUS value for a freshly cloned/approved package.
    // Cloned CA packages are created directly in AP (Approved) state — never CR.
    private static final String CA_PACKAGE_STATUS_APPROVED = "AP";

    @Autowired
    @Qualifier("oracleJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    private final ServiceplanZone servicePlanZone;

    /**
     * Clones a CA Package (and all of its dependent entities) for a newly
     * created CA ATP, and maps the new CA Package to the new Service Package
     * (ATP). Returns null if the old ATP has no CA Package mapped to it.
     *
     * @param oldServicePackageId the source ATP (Service Package) id
     * @param newServicePackageId the newly created ATP (Service Package) id
     * @param networkId           the network id
     * @param suffix              naming suffix (e.g. "ATP12") applied the same
     *                            way it is elsewhere in the clone flow, used to
     *                            build the new (unique) CA Package name
     */
    // public Long cloneCaPackage(Long oldServicePackageId, Long newServicePackageId, Long networkId, String suffix) {

    //     Long oldCaPackageId = findCaPackageId(oldServicePackageId);

    //     if (oldCaPackageId == null) {
    //         logger.info("No CA Package mapped to oldServicePackageId={}. Skipping CA Package clone.",
    //                 oldServicePackageId);
    //         return null;
    //     }

    //     Long newCaPackageId = jdbcTemplate.queryForObject(
    //             "SELECT SEQ_CA_PACKAGE_ID.NEXTVAL FROM DUAL",
    //             Long.class);

    //     String newCaPackageName = buildUniqueCaPackageName(oldCaPackageId, suffix, networkId);

    //     cloneCaPackageMaster(oldCaPackageId, newCaPackageId, newCaPackageName);

    //     cloneAddonMappings(oldCaPackageId, newCaPackageId);

    //     cloneCalendarMappings(oldCaPackageId, newCaPackageId);

    //     cloneServiceUnits(oldCaPackageId, newCaPackageId);

    //     cloneDeviceGroups(oldCaPackageId, newCaPackageId);

    //     cloneDataZoneGroups(oldCaPackageId, newCaPackageId, networkId, suffix);

    //     mapNewServicePackage(newServicePackageId, newCaPackageId, networkId);

    //     logger.info(
    //             "CA Package cloned oldServicePackageId={} newServicePackageId={} oldCaPackageId={} newCaPackageId={} newCaPackageName={}",
    //             oldServicePackageId, newServicePackageId, oldCaPackageId, newCaPackageId, newCaPackageName);

    //     return newCaPackageId;
    // }

    private Long findCaPackageId(Long servicePackageId) {

        String sql = """
                SELECT CA_PACKAGE_ID
                FROM CS_ADD_SVCPACK_CA_PKG_MAP
                WHERE SERVICE_PACKAGE_ID=?
                """;

        List<Long> ids = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong(1),
                servicePackageId);

        return ids.isEmpty() ? null : ids.get(0);
    }

    // /**
    //  * True when the given ATP (Service Package) has a CA Package mapped to it
    //  * in CS_ADD_SVCPACK_CA_PKG_MAP. Used by callers (e.g. the Tariff Package
    //  * update/sync flow) that need to branch on ATP category without going
    //  * through {@link #cloneCaPackage}.
    //  */
    public boolean hasCaPackage(Long servicePackageId) {
        return findCaPackageId(servicePackageId) != null;
    }

    // // ── CA Package cleanup (update/sync flow) ───────────────────────────────
    // // Mirrors cloneCaPackage's child-table list in reverse: when a CA ATP is
    // // removed from a Tariff Package during sync, its cloned CA Package (and
    // // every dependent row) would otherwise be left orphaned, since the normal
    // // deleteAtpHierarchy path only knows about Bundle/Bucket/ATP tables. This
    // // does NOT touch anything the clone flow uses — cloneCaPackage is
    // // unchanged.
    // /**
    //  * Deletes the CA Package mapped to the given ATP (Service Package), along
    //  * with all of its dependent rows, provided no other Service Package still
    //  * references it. Safe to call for a non-CA ATP or one with no CA Package
    //  * mapped — it simply does nothing in that case.
    //  *
    //  * @param servicePackageId the ATP (Service Package) being removed
    //  * @param networkId        the network id
    //  */
    public void deleteCaPackage(Long servicePackageId, Long networkId) {

        Long caPackageId = findCaPackageId(servicePackageId);

        if (caPackageId == null) {
            logger.info("No CA Package mapped to servicePackageId={}. Nothing to clean up.", servicePackageId);
            return;
        }

        try {
            jdbcTemplate.update("""
                    DELETE FROM CS_ADD_SVCPACK_CA_PKG_MAP
                    WHERE SERVICE_PACKAGE_ID = ?
                    """, servicePackageId);
        } catch (Exception ex) {
            throw new TariffInsertException("deleteCaPackage", "CS_ADD_SVCPACK_CA_PKG_MAP", ex);
        }


        try {
            jdbcTemplate.update("DELETE FROM CA_PKG_DATA_ZONE_GROUPS_MAP WHERE CA_PACKAGE_ID = ?", caPackageId);
            jdbcTemplate.update("DELETE FROM CA_PKG_DEVICE_GROUPS_MAP WHERE CA_PACKAGE_ID = ?", caPackageId);
            jdbcTemplate.update("DELETE FROM CA_PACKAGE_SERVICE_UNITS WHERE CA_PACKAGE_ID = ?", caPackageId);
            jdbcTemplate.update("DELETE FROM CA_PACKAGE_SERVICE_CALENDAR WHERE CA_PACKAGE_ID = ?", caPackageId);
            jdbcTemplate.update("DELETE FROM CA_PACKAGE_ADDON_MAP WHERE CA_PACKAGE_ID = ?", caPackageId);
            jdbcTemplate.update("DELETE FROM CA_MT_PACKAGE WHERE CA_PACKAGE_ID = ?", caPackageId);
        } catch (Exception ex) {
            throw new TariffInsertException("deleteCaPackage", "CA_MT_PACKAGE", ex);
        }

        logger.info("CA Package deleted servicePackageId={} caPackageId={} networkId={}", servicePackageId,
                caPackageId, networkId);
    }

    // private boolean isCaPackageReferencedElsewhere(Long caPackageId) {

    //     Integer count = jdbcTemplate.queryForObject("""
    //             SELECT COUNT(1)
    //             FROM CS_ADD_SVCPACK_CA_PKG_MAP
    //             WHERE CA_PACKAGE_ID = ?
    //             """, Integer.class, caPackageId);

    //     return count != null && count > 0;
    // }

    // // ── Unique CA Package naming ──────────────────────────────────────────
    // // Mirrors the "_(CL|TP|ATP)\d+$" stripping pattern used for bundles/buckets
    // // elsewhere in the clone flow, then re-checks CA_MT_PACKAGE for collisions
    // // (the same duplicate-name guard ca_package_insert enforced in Oracle) and
    // // appends an incrementing counter until the name is guaranteed unique.

    // private String buildUniqueCaPackageName(Long oldCaPackageId, String suffix, Long networkId) {

    //     String oldCaPackageName = jdbcTemplate.queryForObject("""
    //             SELECT CA_PACKAGE_NAME
    //             FROM CA_MT_PACKAGE
    //             WHERE CA_PACKAGE_ID = ?
    //             """, String.class, oldCaPackageId);

    //     String baseName = oldCaPackageName.replaceAll("_(CL|TP|ATP)\\d+$", "") + "_" + suffix;

    //     String candidateName = baseName;
    //     int attempt = 0;

    //     while (isDuplicateCaPackageName(candidateName, networkId)) {
    //         attempt++;
    //         candidateName = baseName + "_" + attempt;

    //         logger.info("CA Package name collision, retrying baseName={} attempt={} candidateName={}",
    //                 baseName, attempt, candidateName);
    //     }

    //     return candidateName;
    // }

    // private boolean isDuplicateCaPackageName(String caPackageName, Long networkId) {

    //     Integer count = jdbcTemplate.queryForObject("""
    //             SELECT COUNT(1)
    //             FROM CA_MT_PACKAGE
    //             WHERE CA_HOME_NETWORK_ID = ?
    //             AND UPPER(CA_PACKAGE_NAME) = UPPER(?)
    //             """, Integer.class, networkId, caPackageName);

    //     return count != null && count > 0;
    // }

    // private void cloneCaPackageMaster(Long oldId, Long newId, String newCaPackageName) {

    //     try {
    //         jdbcTemplate.update("""

    //                 INSERT INTO CA_MT_PACKAGE
    //                 (
    //                     CA_PACKAGE_ID,
    //                     CA_PACKAGE_NAME,
    //                     CA_PACKAGE_DEFAULT_LINES,
    //                     CA_PACKAGE_RENTAL_AMOUNT,
    //                     CA_PACKAGE_ADDNL_LINE_CHARGE,
    //                     CA_PACKAGE_ROLLOVER_YN,
    //                     CA_PACKAGE_PLAN,
    //                     CA_PACKAGE_SHELF_DATE,
    //                     CA_HOME_NETWORK_ID,
    //                     PACKAGE_STATUS,
    //                     DATA_ZONE_GROUP_MAP_YN,
    //                     DEVICE_GROUP_MAP_YN,
    //                     CA_PACKAGE_START_DATE
    //                 )

    //                 SELECT
    //                     ?,
    //                     ?,
    //                     CA_PACKAGE_DEFAULT_LINES,
    //                     CA_PACKAGE_RENTAL_AMOUNT,
    //                     CA_PACKAGE_ADDNL_LINE_CHARGE,
    //                     CA_PACKAGE_ROLLOVER_YN,
    //                     CA_PACKAGE_PLAN,
    //                     CA_PACKAGE_SHELF_DATE,
    //                     CA_HOME_NETWORK_ID,
    //                     ?,
    //                     DATA_ZONE_GROUP_MAP_YN,
    //                     DEVICE_GROUP_MAP_YN,
    //                     CA_PACKAGE_START_DATE

    //                 FROM CA_MT_PACKAGE

    //                 WHERE CA_PACKAGE_ID=?

    //                 """,
    //                 newId,
    //                 newCaPackageName,
    //                 CA_PACKAGE_STATUS_APPROVED,
    //                 oldId);
    //     } catch (Exception ex) {
    //         throw new TariffInsertException("cloneCaPackage", "CA_MT_PACKAGE", ex);
    //     }
    // }

    // private void cloneAddonMappings(Long oldId,
    //         Long newId) {

    //     jdbcTemplate.update("""

    //             INSERT INTO CA_PACKAGE_ADDON_MAP
    //             (
    //                 CA_PACKAGE_ID,
    //                 CA_HOME_NETWORK_ID,
    //                 CA_PACKAGE_ADDON_FEATURE_ID,
    //                 CA_PACKAGE_ADDON_CHARGE,
    //                 ADDON_PRIORITY
    //             )

    //             SELECT
    //                 ?,
    //                 CA_HOME_NETWORK_ID,
    //                 CA_PACKAGE_ADDON_FEATURE_ID,
    //                 CA_PACKAGE_ADDON_CHARGE,
    //                 ADDON_PRIORITY

    //             FROM CA_PACKAGE_ADDON_MAP

    //             WHERE CA_PACKAGE_ID=?

    //             """,
    //             newId,
    //             oldId);
    // }

    // private void cloneCalendarMappings(Long oldId,
    //         Long newId) {

    //     jdbcTemplate.update("""

    //             INSERT INTO CA_PACKAGE_SERVICE_CALENDAR
    //             (
    //                 CA_PACKAGE_ID,
    //                 CA_SERVICE_ID,
    //                 CA_CALENDAR_ID,
    //                 CA_VIST_NETWORK_ID,
    //                 CA_HOME_NETWORK_ID
    //             )

    //             SELECT
    //                 ?,
    //                 CA_SERVICE_ID,
    //                 CA_CALENDAR_ID,
    //                 CA_VIST_NETWORK_ID,
    //                 CA_HOME_NETWORK_ID

    //             FROM CA_PACKAGE_SERVICE_CALENDAR

    //             WHERE CA_PACKAGE_ID=?

    //             """,
    //             newId,
    //             oldId);
    // }

    // private void cloneServiceUnits(Long oldId,
    //         Long newId) {

    //     jdbcTemplate.update("""

    //             INSERT INTO CA_PACKAGE_SERVICE_UNITS
    //             (
    //                 CA_PACKAGE_ID,
    //                 CA_SERVICE_ID,
    //                 CA_HOME_NETWORK_ID,
    //                 CA_PACKAGE_UNIT_TYPE,
    //                 CA_PACKAGE_UNIT_VALUE,
    //                 CA_PACKAGE_UNIT_TOPUP_CHARGE,
    //                 CA_SERV_UNIT_MAX_TRANS_PECEN
    //             )

    //             SELECT
    //                 ?,
    //                 CA_SERVICE_ID,
    //                 CA_HOME_NETWORK_ID,
    //                 CA_PACKAGE_UNIT_TYPE,
    //                 CA_PACKAGE_UNIT_VALUE,
    //                 CA_PACKAGE_UNIT_TOPUP_CHARGE,
    //                 CA_SERV_UNIT_MAX_TRANS_PECEN

    //             FROM CA_PACKAGE_SERVICE_UNITS

    //             WHERE CA_PACKAGE_ID=?

    //             """,
    //             newId,
    //             oldId);
    // }

    // private void cloneDeviceGroups(Long oldId,
    //         Long newId) {

    //     jdbcTemplate.update("""

    //             INSERT INTO CA_PKG_DEVICE_GROUPS_MAP
    //             (
    //                 CA_PACKAGE_ID,
    //                 DEVICE_GROUP_ID,
    //                 NETWORK_ID
    //             )

    //             SELECT
    //                 ?,
    //                 DEVICE_GROUP_ID,
    //                 NETWORK_ID

    //             FROM CA_PKG_DEVICE_GROUPS_MAP

    //             WHERE CA_PACKAGE_ID=?

    //             """,
    //             newId,
    //             oldId);
    // }

    // // ── Data Zone Group cloning (Step 5) ────────────────────────────────────
    // // The Oracle procedure only ever inserts CA_PKG_DATA_ZONE_GROUPS_MAP rows
    // // from a caller-supplied pi_data_zone_group_id_arr. During cloning there is
    // // no such input array, so every Data Zone Group already mapped to the old
    // // CA Package has to be discovered, individually cloned (same
    // // generate-id + cloneZoneIfExists pattern used for bucket DATA_ZONE_GROUP_ID
    // // in BundleService), and re-mapped to the new CA Package. No Balance
    // // Category / Service Type / Voice-SMS-Data determination is needed here —
    // // CA_PKG_DATA_ZONE_GROUPS_MAP already stores plain Data Zone Group ids.
    // private void cloneDataZoneGroups(Long oldId, Long newId, Long networkId, String suffix) {

    //     List<Long> oldDataZoneGroupIds = jdbcTemplate.queryForList("""
    //             SELECT DATA_ZONE_GROUP_ID
    //             FROM CA_PKG_DATA_ZONE_GROUPS_MAP
    //             WHERE CA_PACKAGE_ID = ?
    //             """, Long.class, oldId);

    //     for (Long oldDataZoneGroupId : oldDataZoneGroupIds) {

    //         Long newDataZoneGroupId = servicePlanZone
    //                 .generateNewZoneId(ServiceplanZone.ZoneTable.DRE_RATING_GROUP_DETAILS);

    //         boolean cloned = servicePlanZone.cloneZoneIfExists(oldDataZoneGroupId, newDataZoneGroupId, networkId,
    //                 suffix, ServiceplanZone.ZoneTable.DRE_RATING_GROUP_DETAILS);

    //         Long mappedDataZoneGroupId = newDataZoneGroupId;

    //         if (!cloned) {
    //             logger.info(
    //                     "Old DATA_ZONE_GROUP_ID={} not found while cloning CA Package oldCaPackageId={}. Falling back to original id in new mapping.",
    //                     oldDataZoneGroupId, oldId);
    //             mappedDataZoneGroupId = oldDataZoneGroupId;
    //         }

    //         try {
    //             jdbcTemplate.update("""
    //                     INSERT INTO CA_PKG_DATA_ZONE_GROUPS_MAP
    //                     (
    //                         CA_PACKAGE_ID,
    //                         DATA_ZONE_GROUP_ID,
    //                         NETWORK_ID
    //                     )
    //                     VALUES (?,?,?)
    //                     """,
    //                     newId,
    //                     mappedDataZoneGroupId,
    //                     networkId);
    //         } catch (Exception ex) {
    //             throw new TariffInsertException("cloneCaPackage", "CA_PKG_DATA_ZONE_GROUPS_MAP", ex);
    //         }

    //         logger.info(
    //                 "Data Zone Group cloned oldCaPackageId={} newCaPackageId={} oldDataZoneGroupId={} newDataZoneGroupId={}",
    //                 oldId, newId, oldDataZoneGroupId, mappedDataZoneGroupId);
    //     }
    // }

    private void mapNewServicePackage(Long servicePackageId,
                                  Long caPackageId,
                                  Long networkId) {

        try {
            jdbcTemplate.update("""
                    INSERT INTO CS_ADD_SVCPACK_CA_PKG_MAP
                    (
                        SERVICE_PACKAGE_ID,
                        CA_PACKAGE_ID,
                        NETWORK_ID
                    )
                    VALUES (?,?,?)
                    """,
                    servicePackageId,
                    caPackageId,
                    networkId);
        } catch (Exception ex) {
            throw new TariffInsertException("cloneCaPackage", "CS_ADD_SVCPACK_CA_PKG_MAP", ex);
        }
    }

  
    @SuppressWarnings("unchecked")
    public void updateCaPackage(Long servicePackageId, Long networkId, Map<String, Object> caAtp) {

        Long caPackageId = findCaPackageId(servicePackageId);

        if (caPackageId == null) {
            logger.info("No CA Package mapped to servicePackageId={}. Nothing to update.", servicePackageId);
            return;
        }

        Integer defaultLines = intOf(caAtp.get("defaultLinesAllowed"));
        validateMaxLines(defaultLines, networkId);

        // List<Map<String, Object>> addons = (List<Map<String, Object>>) caAtp.getOrDefault("addons", List.of());
        // List<Map<String, Object>> calendarMappings = (List<Map<String, Object>>) caAtp.getOrDefault("calendarMappings",
        //         List.of());
        List<Map<String, Object>> serviceMappings = (List<Map<String, Object>>) caAtp.getOrDefault("serviceMappings",
                List.of());
        List<Object> deviceGroupIds = (List<Object>) caAtp.getOrDefault("deviceGroupIds", List.of());
        List<Object> dataZoneGroupIds = (List<Object>) caAtp.getOrDefault("dataZoneGroupIds", List.of());

        // validateAddonFeatureIds(addons);
        // validateCalendarMappings(calendarMappings, networkId);
        // validateServiceUnitMappings(serviceMappings);

        String dataZoneGroupMapYn = dataZoneGroupIds.isEmpty() ? "N" : "Y";
        String deviceGroupMapYn = deviceGroupIds.isEmpty() ? "N" : "Y";

        try {
            jdbcTemplate.update("""
                    UPDATE CA_MT_PACKAGE
                    SET CA_PACKAGE_DEFAULT_LINES = ?,
                        CA_PACKAGE_RENTAL_AMOUNT = ?,
                        CA_PACKAGE_ADDNL_LINE_CHARGE = ?,
                        CA_PACKAGE_ROLLOVER_YN = ?,
                        CA_PACKAGE_PLAN = ?,
                        CA_PACKAGE_SHELF_DATE = ?,
                        CA_PACKAGE_START_DATE = ?,
                        DATA_ZONE_GROUP_MAP_YN = ?,
                        DEVICE_GROUP_MAP_YN = ?
                    WHERE CA_PACKAGE_ID = ?
                    """,
                    defaultLines,
                    doubleOf(caAtp.get("rental")),
                    doubleOf(caAtp.get("additionalChargePerLine")),
                    stringOf(caAtp.getOrDefault("packageRolloverYn", "N")),
                    stringOf(caAtp.get("validity")),
                    dateOf(caAtp.get("packageEndDate")),
                    dateOf(caAtp.get("packageStartDate")),
                    dataZoneGroupMapYn,
                    deviceGroupMapYn,
                    caPackageId);
        } catch (Exception ex) {
            throw new TariffInsertException("updateCaPackage", "CA_MT_PACKAGE", ex);
        }
        updateServiceUnits(caPackageId, networkId, serviceMappings);

        // try {
        //     jdbcTemplate.update("DELETE FROM CA_PACKAGE_ADDON_MAP WHERE CA_PACKAGE_ID = ?", caPackageId);
        //     jdbcTemplate.update("DELETE FROM CA_PACKAGE_SERVICE_CALENDAR WHERE CA_PACKAGE_ID = ?", caPackageId);
        //     jdbcTemplate.update("DELETE FROM CA_PACKAGE_SERVICE_UNITS WHERE CA_PACKAGE_ID = ?", caPackageId);
        //     jdbcTemplate.update("DELETE FROM CA_PKG_DEVICE_GROUPS_MAP WHERE CA_PACKAGE_ID = ?", caPackageId);
        //     jdbcTemplate.update("DELETE FROM CA_PKG_DATA_ZONE_GROUPS_MAP WHERE CA_PACKAGE_ID = ?", caPackageId);
        // } catch (Exception ex) {
        //     throw new TariffInsertException("updateCaPackage", "CA_MT_PACKAGE child tables", ex);
        // }

       
        // for (Map<String, Object> unit : serviceMappings) {
        //     insertServiceUnit(caPackageId, networkId, unit);
        // }

        logger.info("CA Package updated servicePackageId={} caPackageId={} networkId={}", servicePackageId,
                caPackageId, networkId);
    }


    @SuppressWarnings("unchecked")
    public Long createCaPackage(Long newServicePackageId, Long networkId, String suffix, Map<String, Object> caAtp) {

        // String requestedName = stringOf(caAtp.get("caPackageName"));
        // if (requestedName == null || requestedName.isBlank()) {
        //     throw new IllegalArgumentException("caPackageName is required to create a CA Package");
        // }

        Integer defaultLines = intOf(caAtp.get("defaultLinesAllowed"));
        validateMaxLines(defaultLines, networkId);

        // List<Map<String, Object>> addons = (List<Map<String, Object>>) caAtp.getOrDefault("addons", List.of());
        // List<Map<String, Object>> calendarMappings = (List<Map<String, Object>>) caAtp.getOrDefault("calendarMappings",
        //         List.of());
        List<Map<String, Object>> serviceMappings = (List<Map<String, Object>>) caAtp.getOrDefault("serviceMappings",
                List.of());
        List<Object> deviceGroupIds = (List<Object>) caAtp.getOrDefault("deviceGroupIds", List.of());
        List<Object> dataZoneGroupIds = (List<Object>) caAtp.getOrDefault("dataZoneGroupIds", List.of());

        // validateAddonFeatureIds(addons);
        // validateCalendarMappings(calendarMappings, networkId);
        // validateServiceUnitMappings(serviceMappings);

        Long oldCaPackageId = findCaPackageId(longOf(caAtp.get("servicePackageId")));
        String oldCaPackageName = jdbcTemplate.queryForObject("""
    SELECT CA_PACKAGE_NAME
    FROM CA_MT_PACKAGE
    WHERE CA_PACKAGE_ID = ?
    """, String.class, oldCaPackageId);
         String uniqueName =   oldCaPackageName.replaceAll("_(CL|TP|ATP)\\d+$", "") + "_" + suffix;

        Long newCaPackageId = jdbcTemplate.queryForObject("SELECT SEQ_CA_PACKAGE_ID.NEXTVAL FROM DUAL", Long.class);

        String dataZoneGroupMapYn = dataZoneGroupIds.isEmpty() ? "N" : "Y";
        String deviceGroupMapYn = deviceGroupIds.isEmpty() ? "N" : "Y";

        try {
            jdbcTemplate.update("""
                    INSERT INTO CA_MT_PACKAGE
                    (
                        CA_PACKAGE_ID,
                        CA_PACKAGE_NAME,
                        CA_PACKAGE_DEFAULT_LINES,
                        CA_PACKAGE_RENTAL_AMOUNT,
                        CA_PACKAGE_ADDNL_LINE_CHARGE,
                        CA_PACKAGE_ROLLOVER_YN,
                        CA_PACKAGE_PLAN,
                        CA_PACKAGE_SHELF_DATE,
                        CA_HOME_NETWORK_ID,
                        PACKAGE_STATUS,
                        DATA_ZONE_GROUP_MAP_YN,
                        DEVICE_GROUP_MAP_YN,
                        CA_PACKAGE_START_DATE
                    )
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    newCaPackageId,
                    uniqueName,
                    defaultLines,
                    doubleOf(caAtp.get("rental")),
                    doubleOf(caAtp.get("additionalChargePerLine")),
                    stringOf(caAtp.getOrDefault("packageRolloverYn", "N")),
                    stringOf(caAtp.get("validity")),
                    dateOf(caAtp.get("packageEndDate")),
                    networkId,
                    CA_PACKAGE_STATUS_APPROVED,
                    dataZoneGroupMapYn,
                    deviceGroupMapYn,
                    dateOf(caAtp.get("packageStartDate")));
        } catch (Exception ex) {
            throw new TariffInsertException("createCaPackage", "CA_MT_PACKAGE", ex);
        }

        cloneAddonMappings( oldCaPackageId,newCaPackageId);

        cloneCalendarMappings(oldCaPackageId,newCaPackageId);

       insertServiceUnits(newCaPackageId,networkId,serviceMappings);

       cloneDeviceGroups(oldCaPackageId,newCaPackageId);

        cloneDataZoneGroups( oldCaPackageId,newCaPackageId,networkId,suffix);

        mapNewServicePackage(newServicePackageId, newCaPackageId, networkId);

        logger.info(
                "CA Package created from request newServicePackageId={} newCaPackageId={} newCaPackageName={} networkId={}",
                newServicePackageId, newCaPackageId, uniqueName, networkId);

        return newCaPackageId;
    }

    private void validateMaxLines(Integer defaultLines, Long networkId) {

        if (defaultLines == null) {
            return;
        }

        Integer maxLines = jdbcTemplate.query("""
                SELECT MAX_LINES_PER_CA_PACKAGE
                FROM AMS_MT_CONFIG
                WHERE NETWORK_ID = ?
                """, rs -> rs.next() ? rs.getObject("MAX_LINES_PER_CA_PACKAGE", Integer.class) : null, networkId);

        if (maxLines != null && defaultLines > maxLines) {
            throw new IllegalArgumentException(
                    "Package default lines (" + defaultLines + ") should not be greater than " + maxLines);
        }
    }


    // private void validateAddonFeatureIds(List<Map<String, Object>> addons) {

    //     for (Map<String, Object> addon : addons) {
    //         Long featureId = longOf(addon.get("addonFeatureId"));
    //         if (featureId == null) {
    //             continue;
    //         }
    //         Integer count = jdbcTemplate.queryForObject("""
    //                 SELECT COUNT(1)
    //                 FROM CA_MT_PACKAGE_ADDON_FEATURES
    //                 WHERE CA_ADDON_FEATURE_ID = ?
    //                 """, Integer.class, featureId);
    //         if (count == null || count == 0) {
    //             throw new IllegalArgumentException("Addon feature id not found: " + featureId);
    //         }
    //     }
    // }


    // private void validateCalendarMappings(List<Map<String, Object>> calendarMappings, Long networkId) {

    //     for (Map<String, Object> calendar : calendarMappings) {

    //         Long calendarId = longOf(calendar.get("calendarId"));
    //         if (calendarId != null) {
    //             Integer count = jdbcTemplate.queryForObject("""
    //                     SELECT COUNT(1)
    //                     FROM RAT_MT_CALENDAR
    //                     WHERE CALENDAR_ID = ?
    //                     AND NETWORK_ID = ?
    //                     """, Integer.class, calendarId, networkId);
    //             if (count == null || count == 0) {
    //                 throw new IllegalArgumentException("Invalid calendar id for service plan calendar: " + calendarId);
    //             }
    //         }

    //         Long serviceId = longOf(calendar.get("serviceId"));
    //         if (serviceId != null) {
    //             Integer count = jdbcTemplate.queryForObject("""
    //                     SELECT COUNT(1)
    //                     FROM CA_SERVICE_META_DATA
    //                     WHERE CA_SERVICE_ID = ?
    //                     """, Integer.class, serviceId);
    //             if (count == null || count == 0) {
    //                 throw new IllegalArgumentException("Package Calendar Service id not found: " + serviceId);
    //             }
    //         }
    //     }
    // }

    // Mirrors ca_package_insert's CA_SERVICE_META_DATA check for service
    // units (error 30057). The sample payload's "serviceMappings" entries
    // don't currently carry a "serviceId" — only "serviceUnitType", "units",
    // "topupCharge", "maxTransferLimit". CA_SERVICE_ID is required by
    // CA_PACKAGE_SERVICE_UNITS, so until "serviceId" is added to the
    // payload, entries without it are validated/inserted as a no-op (logged),
    // rather than guessed at.
    // private void validateServiceUnitMappings(List<Map<String, Object>> serviceMappings) {

    //     for (Map<String, Object> unit : serviceMappings) {
    //         Long serviceId = longOf(unit.get("serviceId"));
    //         if (serviceId == null) {
    //             logger.warn("Service mapping has no serviceId — skipping validation for entry: {}", unit);
    //             continue;
    //         }
    //         Integer count = jdbcTemplate.queryForObject("""
    //                 SELECT COUNT(1)
    //                 FROM CA_SERVICE_META_DATA
    //                 WHERE CA_SERVICE_ID = ?
    //                 """, Integer.class, serviceId);
    //         if (count == null || count == 0) {
    //             throw new IllegalArgumentException("Package Service Unit(s) Service id not found: " + serviceId);
    //         }
    //     }
    // }

    // private void insertAddonMapping(Long caPackageId, Long networkId, Map<String, Object> addon) {

    //     try {
    //         jdbcTemplate.update("""
    //                 INSERT INTO CA_PACKAGE_ADDON_MAP
    //                 (
    //                     CA_PACKAGE_ID,
    //                     CA_HOME_NETWORK_ID,
    //                     CA_PACKAGE_ADDON_FEATURE_ID,
    //                     CA_PACKAGE_ADDON_CHARGE,
    //                     ADDON_PRIORITY
    //                 )
    //                 VALUES (?,?,?,?,?)
    //                 """,
    //                 caPackageId,
    //                 networkId,
    //                 longOf(addon.get("addonFeatureId")),
    //                 doubleOf(addon.get("addonCharge")),
    //                 intOf(addon.get("priority")));
    //     } catch (Exception ex) {
    //         throw new TariffInsertException("createCaPackage", "CA_PACKAGE_ADDON_MAP", ex);
    //     }
    // }

    // private void insertCalendarMapping(Long caPackageId, Long networkId, Map<String, Object> calendar) {

    //     try {
    //         jdbcTemplate.update("""
    //                 INSERT INTO CA_PACKAGE_SERVICE_CALENDAR
    //                 (
    //                     CA_PACKAGE_ID,
    //                     CA_SERVICE_ID,
    //                     CA_CALENDAR_ID,
    //                     CA_VIST_NETWORK_ID,
    //                     CA_HOME_NETWORK_ID
    //                 )
    //                 VALUES (?,?,?,?,?)
    //                 """,
    //                 caPackageId,
    //                 longOf(calendar.get("serviceId")),
    //                 longOf(calendar.get("calendarId")),
    //                 longOf(calendar.getOrDefault("visitNetworkId", networkId)),
    //                 networkId);
    //     } catch (Exception ex) {
    //         throw new TariffInsertException("createCaPackage", "CA_PACKAGE_SERVICE_CALENDAR", ex);
    //     }
    // }

    // CA_SERVICE_ID is required here (see validateServiceUnitMappings note
    // above) — an entry with no resolvable serviceId is skipped rather than
    // inserted with a null/guessed id.


    // private void insertServiceUnit(Long caPackageId, Long networkId, Map<String, Object> unit) {

    //     Long serviceId = longOf(unit.get("serviceId"));
    //     if (serviceId == null) {
    //         logger.warn("Skipping CA_PACKAGE_SERVICE_UNITS insert — no serviceId in mapping: {}", unit);
    //         return;
    //     }

    //     try {
    //         jdbcTemplate.update("""
    //                 INSERT INTO CA_PACKAGE_SERVICE_UNITS
    //                 (
    //                     CA_PACKAGE_ID,
    //                     CA_SERVICE_ID,
    //                     CA_HOME_NETWORK_ID,
    //                     CA_PACKAGE_UNIT_TYPE,
    //                     CA_PACKAGE_UNIT_VALUE,
    //                     CA_PACKAGE_UNIT_TOPUP_CHARGE,
    //                     CA_SERV_UNIT_MAX_TRANS_PECEN
    //                 )
    //                 VALUES (?,?,?,?,?,?,?)
    //                 """,
    //                 caPackageId,
    //                 serviceId,
    //                 networkId,
    //                 stringOf(unit.get("serviceUnitType")),
    //                 doubleOf(unit.get("units")),
    //                 doubleOf(unit.get("topupCharge")),
    //                 doubleOf(unit.get("maxTransferLimit")));
    //     } catch (Exception ex) {
    //         throw new TariffInsertException("createCaPackage", "CA_PACKAGE_SERVICE_UNITS", ex);
    //     }
    // }

private void updateServiceUnits(Long caPackageId,
                                Long networkId,
                                List<Map<String, Object>> serviceMappings) {

    if (serviceMappings == null || serviceMappings.isEmpty()) {
        return;
    }

    for (Map<String, Object> unit : serviceMappings) {

        Long serviceId = longOf(unit.get("serviceId"));

        if (serviceId == null) {
            logger.warn("Skipping CA_PACKAGE_SERVICE_UNITS update - no serviceId in mapping: {}", unit);
            continue;
        }

        String serviceUnitType = stringOf(unit.get("serviceUnitType"));
        String unitType = serviceUnitType != null && serviceUnitType.contains("~")
                ? serviceUnitType.substring(serviceUnitType.lastIndexOf("~") + 1)
                : serviceUnitType;

        try {

            int updated = jdbcTemplate.update("""
                    UPDATE CA_PACKAGE_SERVICE_UNITS
                    SET
                        CA_PACKAGE_UNIT_TYPE = ?,
                        CA_PACKAGE_UNIT_VALUE = ?,
                        CA_PACKAGE_UNIT_TOPUP_CHARGE = ?,
                        CA_SERV_UNIT_MAX_TRANS_PECEN = ?
                    WHERE CA_PACKAGE_ID = ?
                      AND CA_SERVICE_ID = ?
                    """,
                    unitType,
                    doubleOf(unit.get("units")),
                    doubleOf(unit.get("topupCharge")),
                    doubleOf(unit.get("maxTransferLimit")),
                    caPackageId,
                    serviceId);

            if (updated == 0) {
                logger.warn(
                        "No CA_PACKAGE_SERVICE_UNITS record found for CA_PACKAGE_ID={} CA_SERVICE_ID={}",
                        caPackageId,
                        serviceId);
            }

        } catch (Exception ex) {
            throw new TariffInsertException(
                    "updateCaPackage",
                    "CA_PACKAGE_SERVICE_UNITS",
                    ex);
        }
    }
}

    // private void insertDeviceGroup(Long caPackageId, Long networkId, Long deviceGroupId) {

    //     if (deviceGroupId == null) {
    //         return;
    //     }

    //     try {
    //         jdbcTemplate.update("""
    //                 INSERT INTO CA_PKG_DEVICE_GROUPS_MAP (CA_PACKAGE_ID, DEVICE_GROUP_ID, NETWORK_ID)
    //                 VALUES (?,?,?)
    //                 """, caPackageId, deviceGroupId, networkId);
    //     } catch (Exception ex) {
    //         throw new TariffInsertException("createCaPackage", "CA_PKG_DEVICE_GROUPS_MAP", ex);
    //     }
    // }

    // Direct mapping of a caller-supplied Data Zone Group id — unlike
    // cloneDataZoneGroups above, there's no old zone to clone from here;
    // these ids are assumed to already exist and be selected as-is.
    // private void insertDataZoneGroupMapping(Long caPackageId, Long networkId, Long dataZoneGroupId) {

    //     if (dataZoneGroupId == null) {
    //         return;
    //     }

    //     try {
    //         jdbcTemplate.update("""
    //                 INSERT INTO CA_PKG_DATA_ZONE_GROUPS_MAP (CA_PACKAGE_ID, DATA_ZONE_GROUP_ID, NETWORK_ID)
    //                 VALUES (?,?,?)
    //                 """, caPackageId, dataZoneGroupId, networkId);
    //     } catch (Exception ex) {
    //         throw new TariffInsertException("createCaPackage", "CA_PKG_DATA_ZONE_GROUPS_MAP", ex);
    //     }
    // }

    private String stringOf(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer intOf(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : Integer.valueOf(s);
    }

    private Long longOf(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : Long.valueOf(s);
    }

    private Double doubleOf(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : Double.valueOf(s);
    }

    // Request payload dates observed as "yyyy-MM-dd" (e.g. "2026-07-31") —
    // different from the "dd/mm/yyyy" ca_package_insert expects, since this
    // binds a java.sql.Date parameter directly instead of building SQL text.
    private java.sql.Date dateOf(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return java.sql.Date.valueOf(LocalDate.parse(s));
        } catch (Exception ex) {
            logger.warn("Could not parse date value '{}' as yyyy-MM-dd — leaving null.", s);
            return null;
        }
    }

    private void cloneAddonMappings(Long oldCaPackageId,
                                Long newCaPackageId) {

    try {
        jdbcTemplate.update("""
            INSERT INTO CA_PACKAGE_ADDON_MAP
            (
                CA_PACKAGE_ID,
                CA_HOME_NETWORK_ID,
                CA_PACKAGE_ADDON_FEATURE_ID,
                CA_PACKAGE_ADDON_CHARGE,
                ADDON_PRIORITY
            )
            SELECT
                ?,
                CA_HOME_NETWORK_ID,
                CA_PACKAGE_ADDON_FEATURE_ID,
                CA_PACKAGE_ADDON_CHARGE,
                ADDON_PRIORITY
            FROM CA_PACKAGE_ADDON_MAP
            WHERE CA_PACKAGE_ID = ?
            """,
            newCaPackageId,
            oldCaPackageId);

    } catch (Exception ex) {
        throw new TariffInsertException(
                "createCaPackage",
                "CA_PACKAGE_ADDON_MAP",
                ex);
    }
}

private void cloneCalendarMappings(Long oldCaPackageId,
                                   Long newCaPackageId) {

    try {
        jdbcTemplate.update("""
            INSERT INTO CA_PACKAGE_SERVICE_CALENDAR
            (
                CA_PACKAGE_ID,
                CA_SERVICE_ID,
                CA_CALENDAR_ID,
                CA_VIST_NETWORK_ID,
                CA_HOME_NETWORK_ID
            )
            SELECT
                ?,
                CA_SERVICE_ID,
                CA_CALENDAR_ID,
                CA_VIST_NETWORK_ID,
                CA_HOME_NETWORK_ID
            FROM CA_PACKAGE_SERVICE_CALENDAR
            WHERE CA_PACKAGE_ID = ?
            """,
            newCaPackageId,
            oldCaPackageId);

    } catch (Exception ex) {
        throw new TariffInsertException(
                "createCaPackage",
                "CA_PACKAGE_SERVICE_CALENDAR",
                ex);
    }
}

private void insertServiceUnits(Long newCaPackageId,
                                Long networkId,
                                List<Map<String, Object>> serviceMappings) {

    for (Map<String, Object> unit : serviceMappings) {

        String serviceUnitType = stringOf(unit.get("serviceUnitType"));
        String unitType = serviceUnitType != null && serviceUnitType.contains("~")
                ? serviceUnitType.substring(serviceUnitType.lastIndexOf("~") + 1)
                : serviceUnitType;

        jdbcTemplate.update("""
                INSERT INTO CA_PACKAGE_SERVICE_UNITS
                (
                    CA_PACKAGE_ID,
                    CA_SERVICE_ID,
                    CA_HOME_NETWORK_ID,
                    CA_PACKAGE_UNIT_TYPE,
                    CA_PACKAGE_UNIT_VALUE,
                    CA_PACKAGE_UNIT_TOPUP_CHARGE,
                    CA_SERV_UNIT_MAX_TRANS_PECEN
                )
                VALUES (?,?,?,?,?,?,?)
                """,
                newCaPackageId,
                longOf(unit.get("serviceId")),
                networkId,
                unitType,
                doubleOf(unit.get("units")),
                doubleOf(unit.get("topupCharge")),
                doubleOf(unit.get("maxTransferLimit")));
    }
}

private void cloneDeviceGroups(Long oldCaPackageId,
                               Long newCaPackageId) {

    try {

        jdbcTemplate.update("""
            INSERT INTO CA_PKG_DEVICE_GROUPS_MAP
            (
                CA_PACKAGE_ID,
                DEVICE_GROUP_ID,
                NETWORK_ID
            )
            SELECT
                ?,
                DEVICE_GROUP_ID,
                NETWORK_ID
            FROM CA_PKG_DEVICE_GROUPS_MAP
            WHERE CA_PACKAGE_ID = ?
            """,
            newCaPackageId,
            oldCaPackageId);

    } catch (Exception ex) {

        throw new TariffInsertException(
                "createCaPackage",
                "CA_PKG_DEVICE_GROUPS_MAP",
                ex);
    }
}

private void cloneDataZoneGroups(Long oldCaPackageId,
                                 Long newCaPackageId,
                                 Long networkId,
                                 String suffix) {

    List<Long> oldZoneIds = jdbcTemplate.queryForList("""
        SELECT DATA_ZONE_GROUP_ID
        FROM CA_PKG_DATA_ZONE_GROUPS_MAP
        WHERE CA_PACKAGE_ID=?
        """,
        Long.class,
        oldCaPackageId);

    for(Long oldZoneId:oldZoneIds){

        Long newZoneId =
                servicePlanZone.generateNewZoneId(
                        ServiceplanZone.ZoneTable.DRE_RATING_GROUP_DETAILS);

        boolean cloned =
                servicePlanZone.cloneZoneIfExists(
                        oldZoneId,
                        newZoneId,
                        networkId,
                        suffix,
                        ServiceplanZone.ZoneTable.DRE_RATING_GROUP_DETAILS);

        jdbcTemplate.update("""
            INSERT INTO CA_PKG_DATA_ZONE_GROUPS_MAP
            (
                CA_PACKAGE_ID,
                DATA_ZONE_GROUP_ID,
                NETWORK_ID
            )
            VALUES (?,?,?)
            """,
            newCaPackageId,
            cloned ? newZoneId : oldZoneId,
            networkId);
    }
}
}