package com.xius.Lb.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-side counterpart of AtpRepository / BucketRepository / BundleRepository /
 * ServicePlanRepository / TariffPlanRepository.
 *
 * Every insert done while creating an ATP (AtpService.createAtp) is undone here
 * as a SELECT, table for table, so that GET /atp/{atpId} can reconstruct the
 * original create payload.
 */
@Repository
public class AtpDetailsRepository {

	private final JdbcTemplate oracleJdbcTemplate;

	public AtpDetailsRepository(JdbcTemplate oracleJdbcTemplate) {
		this.oracleJdbcTemplate = oracleJdbcTemplate;
	}

	// =================================================================
	// ATP / SERVICE PACKAGE (cs_rat_service_package)
	// =================================================================

	public AtpCore findAtpCore(Long atpId) {

		String sql = """
				SELECT network_id,
				       service_package_desc,
				       atp_category_by_offer,
				       description,
				       publicity_id,
				       TO_CHAR(end_date, 'MM/DD/YYYY') AS end_date
				FROM cs_rat_service_package
				WHERE service_package_id = ?
				""";

		List<AtpCore> results = oracleJdbcTemplate.query(sql, (rs, rowNum) -> new AtpCore(rs.getLong("network_id"),
				rs.getString("service_package_desc"), rs.getString("atp_category_by_offer"),
				rs.getString("description"), rs.getString("publicity_id"), rs.getString("end_date")), atpId);

		return results.isEmpty() ? null : results.get(0);
	}

	// =================================================================
	// ATP -> BUNDLE MAPPING (CS_ATP_ACCUMU_BON_DISC_MAP, PLAN_TYPE = 'B')
	// =================================================================

	public Long findBundleIdForAtp(Long atpId) {

		String sql = """
				SELECT bundle_or_discount_id
				FROM CS_ATP_ACCUMU_BON_DISC_MAP
				WHERE atp_id = ?
				  AND plan_type = 'B'
				""";

		List<Long> results = oracleJdbcTemplate.queryForList(sql, Long.class, atpId);

		return results.isEmpty() ? null : results.get(0);
	}

	// =================================================================
	// BUNDLE (bndl_mt_bundle)
	// =================================================================

	public BundleCore findBundle(Long bundleId) {

		String sql = """
				SELECT TO_CHAR(valid_from, 'MM/DD/YYYY') AS valid_from,
				       TO_CHAR(valid_upto, 'MM/DD/YYYY') AS valid_upto,
				       created_by,
				       zone_based_vip_plan_flag_yn
				FROM bndl_mt_bundle
				WHERE bundle_id = ?
				""";

		List<BundleCore> results = oracleJdbcTemplate.query(sql,
				(rs, rowNum) -> new BundleCore(rs.getString("valid_from"), rs.getString("valid_upto"),
						rs.getString("created_by"), rs.getString("zone_based_vip_plan_flag_yn")),
				bundleId);

		return results.isEmpty() ? null : results.get(0);
	}

	// =================================================================
	// BUNDLE <-> BUCKET MAPPING (cs_bndl_mt_bndl_bucket_map)
	// =================================================================

	public List<String> findBucketIdsForBundle(Long bundleId) {

		String sql = """
				SELECT bucket_id
				FROM cs_bndl_mt_bndl_bucket_map
				WHERE bundle_id = ?
				""";

		return oracleJdbcTemplate.queryForList(sql, String.class, bundleId);
	}

	// =================================================================
	// BUCKETS (bndl_mt_buckets)
	// =================================================================

	public List<BucketRow> findBuckets(List<String> bucketIds) {

		if (bucketIds == null || bucketIds.isEmpty()) {
			return Collections.emptyList();
		}

		String placeholders = bucketIds.stream().map(id -> "?").collect(Collectors.joining(","));

		String sql = "SELECT bucket_id, balance_category, usage_type, bucket_unit_type, bucket_unit_value, "
				+ "unlimited_usage_yn, validity_period_days, roll_over_yn, extend_validity_yn, "
				+ "aplicable_from_hrs, aplicable_to_hrs, zone_group_id, data_zone_group_id " + "FROM bndl_mt_buckets "
				+ "WHERE bucket_id IN (" + placeholders + ")";

		RowMapper<BucketRow> mapper = (rs, rowNum) -> new BucketRow(rs.getString("bucket_id"),
				rs.getString("balance_category"), rs.getLong("usage_type"), rs.getString("bucket_unit_type"),
				getNullableLong(rs, "bucket_unit_value"), rs.getString("unlimited_usage_yn"),
				getNullableInteger(rs, "validity_period_days"), rs.getString("roll_over_yn"),
				rs.getString("extend_validity_yn"), getNullableInteger(rs, "aplicable_from_hrs"),
				getNullableInteger(rs, "aplicable_to_hrs"), getNullableLong(rs, "zone_group_id"),
				getNullableLong(rs, "data_zone_group_id"));

		return oracleJdbcTemplate.query(sql, mapper, bucketIds.toArray());
	}

	// =================================================================
	// BUCKET ROAMING NETWORKS (bndl_mt_bucket_roam_nws)
	// =================================================================

	public List<Long> findRoamingNetworksForBuckets(List<String> bucketIds) {

		if (bucketIds == null || bucketIds.isEmpty()) {
			return Collections.emptyList();
		}

		String placeholders = bucketIds.stream().map(id -> "?").collect(Collectors.joining(","));

		String sql = "SELECT DISTINCT roaming_network_id " + "FROM bndl_mt_bucket_roam_nws " + "WHERE bucket_id IN ("
				+ placeholders + ")";

		return oracleJdbcTemplate.queryForList(sql, Long.class, bucketIds.toArray());
	}

	// =================================================================
	// SIM / IMSI RANGES (bndl_mt_sim_imsi_ranges)
	// =================================================================

	public List<SimRangeRow> findSimRanges(Long bundleId) {

		String sql = """
				SELECT include_exclude_flag, sim_or_imsi_flag, range_from, range_to
				FROM bndl_mt_sim_imsi_ranges
				WHERE bundle_id = ?
				""";

		return oracleJdbcTemplate.query(sql,
				(rs, rowNum) -> new SimRangeRow(rs.getString("include_exclude_flag"),
						rs.getString("sim_or_imsi_flag"), rs.getString("range_from"), rs.getString("range_to")),
				bundleId);
	}

	// =================================================================
	// BASIC / DERIVED SERVICE MAPPING (cs_rat_service_atp_map)
	// =================================================================

	public List<String> findDerivedServiceSelections(Long atpId) {

		String sql = """
				SELECT basic_service_id, derived_service_id
				FROM cs_rat_service_atp_map
				WHERE service_package_id = ?
				""";

		return oracleJdbcTemplate.query(sql,
				(rs, rowNum) -> rs.getLong("basic_service_id") + "~" + rs.getLong("derived_service_id"), atpId);
	}

	// =================================================================
	// ATP <-> SERVICE PLAN MAPPING (cs_rat_service_plan_package)
	// =================================================================

	public List<Long> findServicePlanIdsForAtp(Long atpId) {

		String sql = """
				SELECT service_plan_id
				FROM cs_rat_service_plan_package
				WHERE service_package_id = ?
				""";

		return oracleJdbcTemplate.queryForList(sql, Long.class, atpId);
	}

	// =================================================================
	// SERVICE PLANS (cs_rat_service_plans)
	// =================================================================

	public List<ServicePlanRow> findServicePlans(List<Long> servicePlanIds) {

		if (servicePlanIds == null || servicePlanIds.isEmpty()) {
			return Collections.emptyList();
		}

		String placeholders = servicePlanIds.stream().map(id -> "?").collect(Collectors.joining(","));

		String sql = "SELECT service_plan_id, service_plan_type, type_of_service, rating_type, zone_group_id, "
				+ "allow_ntnl_rm_data, allow_int_rm_data, mt_calender_id " + "FROM cs_rat_service_plans "
				+ "WHERE service_plan_id IN (" + placeholders + ")";

		RowMapper<ServicePlanRow> mapper = (rs, rowNum) -> new ServicePlanRow(rs.getLong("service_plan_id"),
				rs.getString("service_plan_type"), getNullableInteger(rs, "type_of_service"),
				rs.getString("rating_type"), getNullableLong(rs, "zone_group_id"), rs.getString("allow_ntnl_rm_data"),
				rs.getString("allow_int_rm_data"), getNullableLong(rs, "mt_calender_id"));

		return oracleJdbcTemplate.query(sql, mapper, servicePlanIds.toArray());
	}

	// =================================================================
	// DATA ZONE MAPPINGS (cs_rat_service_data_zone_map)
	// =================================================================

	public List<Long> findDataZoneMappings(Long dataServicePlanId) {

		if (dataServicePlanId == null) {
			return Collections.emptyList();
		}

		String sql = """
				SELECT data_zone_id
				FROM cs_rat_service_data_zone_map
				WHERE service_plan_id = ?
				""";

		return oracleJdbcTemplate.queryForList(sql, Long.class, dataServicePlanId);
	}

	// =================================================================
	// NULL-SAFE NUMERIC HELPERS
	//
	// Oracle NUMBER columns come back from ResultSet#getObject() as
	// BigDecimal, never Long/Integer, so a direct cast throws
	// ClassCastException. These helpers go through BigDecimal explicitly
	// and preserve NULL (getLong/getInt return 0 for NULL, which would
	// silently corrupt optional columns like zone_group_id).
	// =================================================================

	private Long getNullableLong(ResultSet rs, String column) throws SQLException {

		BigDecimal value = rs.getBigDecimal(column);

		return value == null ? null : value.longValue();
	}

	private Integer getNullableInteger(ResultSet rs, String column) throws SQLException {

		BigDecimal value = rs.getBigDecimal(column);

		return value == null ? null : value.intValue();
	}

	// =================================================================
	// ROW RECORDS
	// =================================================================

	public record AtpCore(Long networkId, String atpName, String categoryOfferCode, String description,
			String publicityId, String validTo) {
	}

	public record BundleCore(String validFrom, String validTo, String createdBy, String vipPlanFlagYn) {
	}

	public record BucketRow(String bucketId, String balanceCategory, Long usageType, String bucketUnitType,
			Long bucketUnitValue, String unlimitedUsageYn, Integer validityPeriodDays, String rollOverYn,
			String extendValidityYn, Integer applicableFromHrs, Integer applicableToHrs, Long zoneGroupId,
			Long dataZoneGroupId) {
	}

	public record SimRangeRow(String includeExcludeFlag, String simImsiFlag, String rangeFrom, String rangeTo) {
	}

	public record ServicePlanRow(Long servicePlanId, String servicePlanType, Integer typeOfService, String ratingType,
			Long zoneGroupId, String allowNationalRoamingData, String allowInternationalRoamingData,
			Long calendarConfig) {
	}
}