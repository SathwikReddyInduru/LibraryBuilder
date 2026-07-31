package com.xius.TariffBuilder.UserService;

// import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.xius.TariffBuilder.exception.TariffInsertException;

import jakarta.transaction.Transactional;

@Service
public class ServiceplanZone {

    private static final Logger logger = LoggerFactory.getLogger(ServiceplanZone.class);

    private static final int DATA_TYPE_OF_SERVICE = 3;

    private static final String DATA_BALANCE_CATEGORY = "DATA";


    public enum ZoneTable {

        RAT_ZONE_GROUPS("CS_RAT_ZONE_GROUPS", "seq_zone_group_id"),
        DRE_RATING_GROUP_DETAILS("CS_DRE_RATING_GROUP_DETAILS", "seq_dre_zone_group_id");

        private final String tableName;
        private final String sequenceName;

        ZoneTable(String tableName, String sequenceName) {
            this.tableName = tableName;
            this.sequenceName = sequenceName;
        }

        public String getTableName() {
            return tableName;
        }
        public String getSequenceName() {
            return sequenceName;
        }
    }

    @Qualifier("oracleJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    // private final DayTypeCloneService dayTypeCloneService;

    // ServiceplanZone(JdbcTemplate jdbcTemplate, DayTypeCloneService dayTypeCloneService) {
    //     this.jdbcTemplate = jdbcTemplate;
    //     this.dayTypeCloneService = dayTypeCloneService;
    // }

    ServiceplanZone(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
}
  
    public ZoneTable resolveZoneTableByTypeOfService(Integer typeOfService) {

        if (typeOfService != null && typeOfService == DATA_TYPE_OF_SERVICE) {
            return ZoneTable.DRE_RATING_GROUP_DETAILS;
        }

        return ZoneTable.RAT_ZONE_GROUPS;
    }

  
    public ZoneTable resolveZoneTableByBalanceCategory(String balanceCategory) {

        if (balanceCategory != null && DATA_BALANCE_CATEGORY.equalsIgnoreCase(balanceCategory)) {
            return ZoneTable.DRE_RATING_GROUP_DETAILS;
        }

        return ZoneTable.RAT_ZONE_GROUPS;
    }

    public Long generateNewZoneId(ZoneTable zoneTable) {
        return jdbcTemplate.queryForObject(
                "select " + zoneTable.getSequenceName() + ".NEXTVAL from dual",
                Long.class);
    }

     private Integer countZone(String tableName, Long zoneId) {

        if (zoneId == null) {
            return 0;
        }

        return jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where ZONE_GROUP_ID = ?",
                Integer.class,
                zoneId);
    }


    @Transactional
    public boolean cloneZoneIfExists(Long oldZoneId, Long newZoneId, Long networkId, String tpName, ZoneTable zoneTable) {

        if (oldZoneId == null || newZoneId == null) {
            logger.info("Zone clone skipped oldZoneId={} newZoneId={}", oldZoneId, newZoneId);
            return false;
        }

        String suffix = "_" + tpName;
        String tableName = zoneTable.getTableName();

        Integer oldCount = countZone(tableName, oldZoneId);

        if (oldCount == 0) {
            logger.info("Old zone id {} not found in {}. Skipping zone clone.", oldZoneId, tableName);
            return false;
        }

        Integer newCount = countZone(tableName, newZoneId);

        if (newCount == 0) {

            switch (zoneTable) {
                case RAT_ZONE_GROUPS -> cloneRatZoneGroup(oldZoneId, newZoneId, suffix);
                case DRE_RATING_GROUP_DETAILS -> cloneDreRatingGroupDetails(oldZoneId, newZoneId, suffix);
            }

            logger.info("{} cloned oldZoneId={} newZoneId={}", tableName, oldZoneId, newZoneId);
        } else {
            logger.info("{} already has newZoneId={}. Skipping insert.", tableName, newZoneId);
        }

        cloneSlabAndCalendar(oldZoneId, newZoneId, networkId, suffix);
        logger.info("Zone clone completed oldZoneId={} newZoneId={} table={}", oldZoneId, newZoneId, tableName);

        return true;
    }


    private void cloneRatZoneGroup(Long oldZoneId, Long newZoneId, String suffix) {

        try {
            jdbcTemplate.update(
                    """
                            insert into CS_RAT_ZONE_GROUPS
                            (
                                ZONE_GROUP_ID,
                                ZONE_GROUP_DESC,
                                NETWORK_ID,
                                TYPE_OF_SERVICE,
                                RATING_YN
                            )
                            select
                                ?,
                                REGEXP_REPLACE(ZONE_GROUP_DESC,'_(CL|TP|ATP)[0-9]+$','') || ?,
                                NETWORK_ID,
                                TYPE_OF_SERVICE,
                                RATING_YN
                            from CS_RAT_ZONE_GROUPS
                            where ZONE_GROUP_ID = ?
                            """,
                    newZoneId,
                    suffix,
                    oldZoneId);
        } catch (Exception ex) {
            throw new TariffInsertException("cloneZoneGroup", "CS_RAT_ZONE_GROUPS", ex);
        }
    }

    private void cloneDreRatingGroupDetails(Long oldZoneId, Long newZoneId, String suffix) {

        try {
            jdbcTemplate.update(
                    """
                            insert into CS_DRE_RATING_GROUP_DETAILS
                            (
                                NETWORK_ID,
                                ROAMING_NETWORK_ID,
                                ZONE_GROUP_ID,
                                ZONE_GROUP_NAME,
                                APN_ID,
                                RATING_GROUP_ID,
                                CALENDAR_ID,
                                RATING_YN
                            )
                            select
                                NETWORK_ID,
                                ROAMING_NETWORK_ID,
                                ?,
                                REGEXP_REPLACE(ZONE_GROUP_NAME,'_(CL|TP|ATP)[0-9]+$','') || ?,
                                APN_ID,
                                RATING_GROUP_ID,
                                CALENDAR_ID,
                                RATING_YN
                            from CS_DRE_RATING_GROUP_DETAILS
                            where ZONE_GROUP_ID = ?
                            """,
                    newZoneId,
                    suffix,
                    oldZoneId);
        } catch (Exception ex) {
            throw new TariffInsertException("cloneRatingGroupDetails", "CS_DRE_RATING_GROUP_DETAILS", ex);
        }
    }


    private void cloneSlabAndCalendar(Long oldZoneId, Long newZoneId, Long networkId, String suffix) {

        try {
            jdbcTemplate.update(
                    """
                            insert into CS_RAT_ZONEGROUP_SLAB_MAPPING
                            (
                                ZONE_GROUP_ID,
                                SLAB_ID,
                                AIRTIME_CALENDAR,
                                NETWORK_ID
                            )
                            select
                                ?,
                                old.SLAB_ID,
                                old.AIRTIME_CALENDAR,
                                ?
                            from CS_RAT_ZONEGROUP_SLAB_MAPPING old
                            where old.ZONE_GROUP_ID = ?
                              and not exists (
                                  select 1
                                  from CS_RAT_ZONEGROUP_SLAB_MAPPING existing
                                  where existing.ZONE_GROUP_ID = ?
                                    and existing.SLAB_ID = old.SLAB_ID
                                    and existing.NETWORK_ID = ?
                              )
                            """,
                    newZoneId, networkId, oldZoneId, newZoneId, networkId);
        } catch (Exception ex) {
            throw new TariffInsertException("cloneSlabAndCalendar", "CS_RAT_ZONEGROUP_SLAB_MAPPING", ex);
        }
    }
}

    // private Long cloneCalendar(Long oldCalendarId,
    //         String suffix,
    //         Long networkId,
    //         Map<Long, Long> calendarCache) {

    //     if (oldCalendarId == null) {
    //         return null;
    //     }

    //     if (calendarCache.containsKey(oldCalendarId)) {
    //         return calendarCache.get(oldCalendarId);
    //     }

    //     // ── Read the original calendar row ───────────────────────────────────
    //     Map<String, Object> oldCalRow = jdbcTemplate.queryForMap(
    //             """
    //                     select *
    //                     from RAT_MT_CALENDAR
    //                     where CALENDAR_ID = ?
    //                     """,
    //             oldCalendarId);

    //     // ── Extract the 7 original DayType IDs ──────────────────────────────
    //     Long oldSunday = toLong(oldCalRow.get("SUNDAY_DAYTYPE"));
    //     Long oldMonday = toLong(oldCalRow.get("MONDAY_DAYTYPE"));
    //     Long oldTuesday = toLong(oldCalRow.get("TUESDAY_DAYTYPE"));
    //     Long oldWednesday = toLong(oldCalRow.get("WEDNESDAY_DAYTYPE"));
    //     Long oldThursday = toLong(oldCalRow.get("THURSDAY_DAYTYPE"));
    //     Long oldFriday = toLong(oldCalRow.get("FRIDAY_DAYTYPE"));
    //     Long oldSaturday = toLong(oldCalRow.get("SATURDAY_DAYTYPE"));

    //     logger.info(
    //             "Original calendar CALENDAR_ID={} DayTypes: Sun={} Mon={} Tue={} Wed={} Thu={} Fri={} Sat={}",
    //             oldCalendarId,oldSunday, oldMonday, oldTuesday, oldWednesday,oldThursday, oldFriday, oldSaturday);

    //     // ── Clone each distinct DayType; cache prevents duplicate cloning ────
    //     Map<Long, Long> dayTypeCache = new HashMap<>();

    //     Long newSunday = dayTypeCloneService.cloneDayType(oldSunday, networkId, suffix, dayTypeCache);
    //     Long newMonday = dayTypeCloneService.cloneDayType(oldMonday, networkId, suffix, dayTypeCache);
    //     Long newTuesday = dayTypeCloneService.cloneDayType(oldTuesday, networkId, suffix, dayTypeCache);
    //     Long newWednesday = dayTypeCloneService.cloneDayType(oldWednesday, networkId, suffix, dayTypeCache);
    //     Long newThursday = dayTypeCloneService.cloneDayType(oldThursday, networkId, suffix, dayTypeCache);
    //     Long newFriday = dayTypeCloneService.cloneDayType(oldFriday, networkId, suffix, dayTypeCache);
    //     Long newSaturday = dayTypeCloneService.cloneDayType(oldSaturday, networkId, suffix, dayTypeCache);

    //     logger.info(
    //             "DayType mappings for CALENDAR_ID={}: Sun={}->{} Mon={}->{} Tue={}->{} Wed={}->{} Thu={}->{} Fri={}->{} Sat={}->{}",
    //             oldCalendarId,
    //             oldSunday, newSunday,
    //             oldMonday, newMonday,
    //             oldTuesday, newTuesday,
    //             oldWednesday, newWednesday,
    //             oldThursday, newThursday,
    //             oldFriday, newFriday,
    //             oldSaturday, newSaturday);

    //     // ── Generate new CALENDAR_ID ─────────────────────────────────────────
    //     Long newCalendarId = jdbcTemplate.queryForObject(
    //             """
    //                     select nvl(max(CALENDAR_ID),0)+1 from RAT_MT_CALENDAR  """,  Long.class);

    //     logger.info(
    //             "Inserting RAT_MT_CALENDAR newCalendarId={} with newDayTypes: Sun={} Mon={} Tue={} Wed={} Thu={} Fri={} Sat={}",
    //             newCalendarId,  newSunday, newMonday, newTuesday, newWednesday,  newThursday, newFriday, newSaturday);

    //     // ── Insert the cloned calendar row using NEW DayType IDs ─────────────
    //     try {
    //         jdbcTemplate.update(
    //                 """
    //                         insert into RAT_MT_CALENDAR
    //                         (
    //                             CALENDAR_ID,
    //                             SUNDAY_DAYTYPE,
    //                             MONDAY_DAYTYPE,
    //                             TUESDAY_DAYTYPE,
    //                             WEDNESDAY_DAYTYPE,
    //                             THURSDAY_DAYTYPE,
    //                             FRIDAY_DAYTYPE,
    //                             SATURDAY_DAYTYPE,
    //                             CALENDAR_NAME,
    //                             DESCRIPTION,
    //                             NETWORK_ID,
    //                             DURATION_VOLUME_FLAG
    //                         )
    //                         select
    //                             ?,
    //                             ?,
    //                             ?,
    //                             ?,
    //                             ?,
    //                             ?,
    //                             ?,
    //                             ?,
    //                             REGEXP_REPLACE(CALENDAR_NAME,'_(CL|TP|ATP)[0-9]+$','') || ?,
    //                             REGEXP_REPLACE(DESCRIPTION,'_(CL|TP|ATP)[0-9]+$','') || ?,
    //                             ?,
    //                             DURATION_VOLUME_FLAG
    //                         from RAT_MT_CALENDAR
    //                         where CALENDAR_ID = ?
    //                         """,
    //                 newCalendarId,  newSunday,  newMonday,  newTuesday, newWednesday,   newThursday, newFriday, newSaturday, suffix, suffix, networkId, oldCalendarId);
    //     } catch (Exception ex) {
    //         logger.error("Error cloning RAT_MT_CALENDAR oldCalendarId={}", oldCalendarId, ex);
    //         throw new TariffInsertException("cloneCalendar", "RAT_MT_CALENDAR", ex);
    //     }

    //     calendarCache.put(oldCalendarId, newCalendarId);
    //     logger.info("RAT_MT_CALENDAR cloned oldCalendarId={} newCalendarId={}", oldCalendarId, newCalendarId);

    //     return newCalendarId;
    // }

//     private Long toLong(Object value) {
//         if (value == null) {
//             return null;
//         }
//         return ((Number) value).longValue();
//     }
// }