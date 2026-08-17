package com.xius.Lb.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BundleRepository {

	private final JdbcTemplate oracleJdbcTemplate;

	public BundleRepository(JdbcTemplate oracleJdbcTemplate) {

		this.oracleJdbcTemplate = oracleJdbcTemplate;
	}

//    public boolean existsBundle(
//            String bundleName,
//            Long networkId) {
//
//        String sql = """
//                SELECT COUNT(1)
//                FROM bndl_mt_bundle
//                WHERE bundle_name = ?
//                  AND network_id = ?
//                """;
//
//        Integer count =
//                oracleJdbcTemplate.queryForObject(
//                        sql,
//                        Integer.class,
//                        bundleName,
//                        networkId
//                );
//
//        return count != null && count > 0;
//    }

	public Long getNextBundleId() {

		String sql = """
				SELECT seq_bundle_id.NEXTVAL
				FROM DUAL
				""";

		return oracleJdbcTemplate.queryForObject(sql, Long.class);
	}

	public void insertBundle(Long bundleId, String bundleName, String validFrom, String validTo, Long customerGroupId,
			String createdBy, Long networkId, String activationNotificationType, Number activationCharge,
			String expiryNotificationType, Number expiryCharge, Integer expiryNotificationThreshold,
			String immediateFlag, String bundleCategory, Integer planExpNotifThresholdHrs,
			String zoneBasedVipPlanFlagYn) {

		String sql = """
				INSERT INTO bndl_mt_bundle
				(
				    bundle_id,
				    bundle_name,
				    bundle_charge,
				    purchage_type,
				    valid_from,
				    valid_upto,
				    customer_group_id,
				    bundle_status,
				    status_date,
				    created_date,
				    created_by,
				    network_id,
				    activation_notification_type,
				    expiry_notification_type,
				    immediate_benefit,
				    bundle_category,
				    zone_based_vip_plan_flag_yn
				)
				VALUES
				(
				    ?,
				    UPPER(?),
				    NULL,
				    NULL,
				    TO_DATE(?, 'MM/DD/YYYY'),
				    TO_DATE(?, 'MM/DD/YYYY'),
				    ?,
				    'AP',
				    SYSDATE,
				    SYSDATE,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?
				)
				""";

		oracleJdbcTemplate.update(sql, bundleId, bundleName, validFrom, validTo, customerGroupId, createdBy, networkId,
				activationNotificationType, expiryNotificationType, immediateFlag, bundleCategory,
				zoneBasedVipPlanFlagYn);
	}

	public void insertBundleBucketMapping(Long bundleId, String bucketId, Long networkId) {

		String sql = """
				INSERT INTO cs_bndl_mt_bndl_bucket_map
				(
				    bundle_id,
				    bucket_id,
				    network_id
				)
				VALUES
				(
				    ?,
				    ?,
				    ?
				)
				""";

		oracleJdbcTemplate.update(sql, bundleId, bucketId, networkId);
	}

	public void insertSimImsiRange(Long bundleId, String includeExcludeFlag, String simImsiFlag, String rangeFrom,
			String rangeTo, Long networkId) {

		String sql = """
				INSERT INTO bndl_mt_sim_imsi_ranges
				(
				    bundle_id,
				    include_exclude_flag,
				    sim_or_imsi_flag,
				    range_from,
				    range_to,
				    network_id
				)
				VALUES
				(
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?
				)
				""";

		oracleJdbcTemplate.update(sql, bundleId, includeExcludeFlag, simImsiFlag, rangeFrom, rangeTo, networkId);
	}
}