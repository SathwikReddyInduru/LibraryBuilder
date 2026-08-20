package com.xius.Lb.repo;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.xius.Lb.Dto.BucketUsageTypeResponse;
import com.xius.Lb.Dto.CalendarResponse;
import com.xius.Lb.Dto.DerivedServiceResponse;
import com.xius.Lb.Dto.ZoneGroupResponse;

@Repository
public class LibraryGetRepository {
    

     private final JdbcTemplate oracleJdbcTemplate;

	public LibraryGetRepository(JdbcTemplate oracleJdbcTemplate) {

		this.oracleJdbcTemplate = oracleJdbcTemplate;
	}

    public List<DerivedServiceResponse> getDerivedServices() {
 
        String sql = """
                SELECT a.basic_service_id,
                    b.derived_service_id,
                    b.service_name AS derived_svc_name,
                    b.zone_group_yn
                FROM cs_rat_mt_services_map a,
                    cs_rat_mt_derived_service b
                WHERE a.derived_service_id = b.derived_service_id
                """;
    
        return oracleJdbcTemplate.query(
                sql,
                (rs, rowNum) -> new DerivedServiceResponse(
                        rs.getLong("basic_service_id"),
                        rs.getLong("derived_service_id"),
                        rs.getString("derived_svc_name"),
                        rs.getString("zone_group_yn")
                )
        );
    }

    public List<BucketUsageTypeResponse> getBucketUsageTypes() {
 
        String sql = """
                SELECT usage_binary_id,
                    usage_type,
                    balance_category
                FROM bndl_mt_bucket_usage_types
                ORDER BY usage_binary_id
                """;
    
        return oracleJdbcTemplate.query(
                sql,
                (rs, rowNum) -> new BucketUsageTypeResponse(
                        rs.getLong("usage_binary_id"),
                        rs.getString("usage_type"),
                        rs.getString("balance_category")
                )
        );
    }

    public List<CalendarResponse> getCalendars(Long networkId) {

        String sql = """
                SELECT calendar_id,
                       UPPER(calendar_name) AS calendar_name
                  FROM rat_mt_calendar
                 WHERE network_id = ?
                 ORDER BY UPPER(calendar_name) ASC
                """;

        return oracleJdbcTemplate.query(
                sql,
                (rs, rowNum) -> new CalendarResponse(
                        rs.getLong("calendar_id"),
                        rs.getString("calendar_name")
                ),
                networkId
        );
    }

    public List<ZoneGroupResponse> getVoiceSmsZoneGroups(Long networkId) {

        String sql = """
                SELECT zone_group_id,
                       UPPER(zone_group_desc) AS zone_group_name,
                       rating_yn
                  FROM cs_rat_zone_groups
                 WHERE network_id = ?
                 ORDER BY UPPER(zone_group_desc) ASC
                """;

        return oracleJdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ZoneGroupResponse(
                        rs.getLong("zone_group_id"),
                        rs.getString("zone_group_name"),
                        rs.getString("rating_yn")),
                networkId);
    }

    // Data
    public List<ZoneGroupResponse> getDataZoneGroups(Long networkId) {

        String sql = """
                SELECT DISTINCT zone_group_id,
                       UPPER(zone_group_name) AS zone_group_name,rating_yn
                  FROM cs_dre_rating_group_details
                 WHERE network_id = ?
                 ORDER BY UPPER(zone_group_name) ASC
                """;

        return oracleJdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ZoneGroupResponse(
                        rs.getLong("zone_group_id"),
                        rs.getString("zone_group_name"),
                        rs.getString("rating_yn")),
                networkId);
    }
}