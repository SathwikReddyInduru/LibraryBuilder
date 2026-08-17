package com.xius.Lb.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BucketRepository {

	private final JdbcTemplate oracleJdbcTemplate;

	public BucketRepository(JdbcTemplate oracleJdbcTemplate) {

		this.oracleJdbcTemplate = oracleJdbcTemplate;
	}

	public Long getNextBucketSequence() {

		String sql = """
				SELECT seq_bucket_id.NEXTVAL
				FROM DUAL
				""";

		return oracleJdbcTemplate.queryForObject(sql, Long.class);
	}

	public boolean existsPriority(Integer priority, String balanceCategory) {

		String sql = """
				SELECT COUNT(1)
				FROM bndl_mt_buckets
				WHERE priority = ?
				  AND balance_category = ?
				""";

		Integer count = oracleJdbcTemplate.queryForObject(sql, Integer.class, priority, balanceCategory);

		return count != null && count > 0;
	}

	public void insertBucket(String bucketId, String bucketName, String balanceCategory, Long usageType,
			Integer validityPeriodDays, Long bucketUnitValue, String bucketUnitType, String iterativeBucketYn,
			Integer iterativeCounts, String rollOverYn, String extendValidityYn, String createdBy, Long networkId,
			Integer duration, Long zoneGroupId, Long dataZoneGroupId, String limitedNetworksYn, String limitedHours,
			Integer applicableFromHrs, Integer applicableToHrs, Integer priority, Long deviceGroupId,
			String unlimitedUsageYn, Integer dayType) {

		String sql = """
				INSERT INTO bndl_mt_buckets
				(
				    bucket_id,
				    bucket_name,
				    balance_category,
				    usage_type,
				    validity_period_days,
				    bucket_unit_value,
				    bucket_unit_type,
				    iterative_bucket_yn,
				    iterative_counts,
				    roll_over_yn,
				    extend_validity_yn,
				    bucket_status,
				    status_date,
				    created_date,
				    created_by,
				    network_id,
				    duration,
				    zone_group_id,
				    data_zone_group_id,
				    limited_networks_yn,
				    limited_hours,
				    aplicable_from_hrs,
				    aplicable_to_hrs,
				    priority,
				    device_group_id,
				    unlimited_usage_yn,
				    day_type
				)
				VALUES
				(
				    ?,
				    UPPER(?),
				    ?,
				    ?,
				    NVL(?, -1),
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    'AP',
				    SYSDATE,
				    SYSDATE,
				    ?,
				    ?,
				    NVL(?, NULL),
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    NVL(?, 0)
				)
				""";

		oracleJdbcTemplate.update(sql,

				bucketId, bucketName, balanceCategory, usageType, validityPeriodDays, bucketUnitValue, bucketUnitType,
				iterativeBucketYn, iterativeCounts, rollOverYn, extendValidityYn, createdBy, networkId, duration,
				zoneGroupId, dataZoneGroupId, limitedNetworksYn, limitedHours, applicableFromHrs, applicableToHrs,
				priority, deviceGroupId, unlimitedUsageYn, dayType);
	}

	public void insertBucketRoamingNetwork(Long networkId, String bucketId, Long roamingNetworkId) {

		String sql = """
				INSERT INTO bndl_mt_bucket_roam_nws
				(
				    home_network_id,
				    bucket_id,
				    roaming_network_id
				)
				VALUES
				(
				    ?,
				    ?,
				    ?
				)
				""";

		oracleJdbcTemplate.update(sql, networkId, bucketId, roamingNetworkId);
	}
}
