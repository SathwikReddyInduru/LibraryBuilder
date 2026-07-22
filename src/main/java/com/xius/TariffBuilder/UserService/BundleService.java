package com.xius.TariffBuilder.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

import com.xius.TariffBuilder.exception.TariffInsertException;

@Service
public class BundleService {

	private static final Logger logger = LoggerFactory.getLogger(BundleService.class);

	@Qualifier("oracleJdbcTemplate")
	private final JdbcTemplate jdbcTemplate;

	private final ServiceCloneService serviceCloneService;

	private final ServiceplanZone servicePlanZone;

	BundleService(JdbcTemplate jdbcTemplate, ServiceCloneService serviceCloneService, ServiceplanZone servicePlanZone) {
		this.jdbcTemplate = jdbcTemplate;
		this.serviceCloneService = serviceCloneService;
		this.servicePlanZone = servicePlanZone;
	}

	public static class CloneAtpResult {

		private Long newAtpId;
		private String oldBucketId;
		private String newBucketId;
		private Long newBucketZoneId;
		private List<Long> newPlanIds;

		public CloneAtpResult(Long newAtpId, String oldBucketId, String newBucketId, Long newBucketZoneId,
				List<Long> newPlanIds) {
			this.newAtpId = newAtpId;
			this.oldBucketId = oldBucketId;
			this.newBucketId = newBucketId;
			this.newBucketZoneId = newBucketZoneId;
			this.newPlanIds = newPlanIds;
		}

		public Long getNewAtpId() {
			return newAtpId;
		}

		public String getOldBucketId() {
			return oldBucketId;
		}

		public String getNewBucketId() {
			return newBucketId;
		}

		public Long getNewBucketZoneId() {
			return newBucketZoneId;
		}

		public List<Long> getNewPlanIds() {
			return newPlanIds;
		}
	}



	
	 // Get first old bucket id from ATP.
	 
	public String getOldBucketId(Long atpId, Long networkId) {

		return jdbcTemplate.query("""
				select bm.BUCKET_ID
				from CS_ATP_ACCUMU_BON_DISC_MAP am
				join CS_BNDL_MT_BNDL_BUCKET_MAP bm
				  on bm.BUNDLE_ID = am.BUNDLE_OR_DISCOUNT_ID
				where am.ATP_ID = ?
				and am.NETWORK_ID = ?
				fetch first 1 rows only
				""", rs -> rs.next() ? rs.getString("BUCKET_ID") : null, atpId, networkId);
	}

	
	 //Get zone id from old bucket.
	 
	public Long getBucketZoneId(String bucketId) {

		if (bucketId == null) {
			return null;
		}

		return jdbcTemplate.query("""
				select ZONE_GROUP_ID
				from BNDL_MT_BUCKETS
				where BUCKET_ID = ?
				""", rs -> rs.next() ? rs.getObject("ZONE_GROUP_ID", Long.class) : null, bucketId);
	}

	 // Generate new bucket zone id based on bucket table. Delegates to
	 // ServiceplanZone so there's one place (ZoneTable.getSequenceName())
	 // that knows which sequence backs which table.
	public Long generateNewBucketZoneId() {
		return servicePlanZone.generateNewZoneId(ServiceplanZone.ZoneTable.RAT_ZONE_GROUPS);
	}

	// DATA_ZONE_GROUP_ID is a second, independent zone reference on
	// BNDL_MT_BUCKETS — separate from ZONE_GROUP_ID — same pattern as
	// CS_RAT_SERVICE_PLANS.DATA_ZONE_GROUP_ID in
	// ServiceCloneService.cloneServicePlans. It always clones into
	// CS_DRE_RATING_GROUP_DETAILS, whenever the source bucket has one.
	public Long getBucketDataZoneGroupId(String bucketId) {

		if (bucketId == null) {
			return null;
		}

		return jdbcTemplate.query("""
				select DATA_ZONE_GROUP_ID
				from BNDL_MT_BUCKETS
				where BUCKET_ID = ?
				""", rs -> rs.next() ? rs.getObject("DATA_ZONE_GROUP_ID", Long.class) : null, bucketId);
	}

	// Buckets don't carry TYPE_OF_SERVICE, but they do carry BALANCE_CATEGORY,
	// which is the discriminator ServiceplanZone.resolveZoneTableByBalanceCategory
	// uses to decide which zone table (RAT vs DRE) to clone through.
	public String getBucketBalanceCategory(String bucketId) {

		if (bucketId == null) {
			return null;
		}

		return jdbcTemplate.query("""
				select BALANCE_CATEGORY
				from BNDL_MT_BUCKETS
				where BUCKET_ID = ?
				""", rs -> rs.next() ? rs.getString("BALANCE_CATEGORY") : null, bucketId);
	}

	
	 //Clone ATP, bundle, and bucket. Bucket will use newBucketZoneId.

	@Transactional
	public CloneAtpResult cloneAtpData(Long atpId, Long networkId, String tpName, Long newBucketZoneId) {

		logger.info("ATP clone started atpId={} networkId={} tpName={} newBucketZoneId={}", atpId, networkId, tpName,
				newBucketZoneId);

		Long newAtpId = jdbcTemplate.queryForObject("""
				SELECT SEQ_SERVICE_PACK_ID.NEXTVAL FROM DUAL
				""", Long.class);

		Map<String, Object> oldAtp = jdbcTemplate.queryForMap("""
				select *
				from CS_RAT_SERVICE_PACKAGE
				where SERVICE_PACKAGE_ID = ?
				""", atpId);

		try {
			jdbcTemplate.update("""
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
					values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
					""",
					newAtpId,
					oldAtp.get("SERVICE_PACKAGE_DESC").toString().replaceAll("_(CL|TP|ATP)\\d+$", "") + "_" + tpName,
					oldAtp.get("RENTAL_AMOUNT"),
					oldAtp.get("ACTIVATION_CHARGE"),
					networkId,
					oldAtp.get("TAX1"),
					oldAtp.get("TAX2"),
					oldAtp.get("TAX3"),
					oldAtp.get("CHARGE_ID"),
					oldAtp.get("ADD_PACK_YN"),
					oldAtp.get("RENTAL_TYPE"),
					oldAtp.get("RENTAL_PERIOD"),
					oldAtp.get("ASP_TYPE"),
					oldAtp.get("END_DATE"),
					oldAtp.get("SERVICE_DURATION"),
					oldAtp.get("ATP_CATEGORY"),
					oldAtp.get("TRANSFEROR_CHARGE"),
					oldAtp.get("TRANSFEREE_CHARGE"),
					oldAtp.get("CHANGE_MSISDN_CHARGE"),
					oldAtp.get("MAX_AMT_PER_TRANS"),
					oldAtp.get("PUBLICITY_ID") != null
							? oldAtp.get("PUBLICITY_ID").toString().replaceAll("_(CL|TP|ATP)\\d+$", "") + "_" + tpName
							: null,
					oldAtp.get("MAX_FNFSERVICE_NUMBERS"),
					oldAtp.get("MAX_SMSSERVICE_NUMBERS"),
					oldAtp.get("CA_SERVICE_PACKAGE_YN"),
					oldAtp.get("ATP_CATEGORY_BY_OFFER"),
					oldAtp.get("DESCRIPTION"),
					oldAtp.get("CHARGE_ON_FIRST_USAGE_YN"));
		} catch (Exception ex) {
			throw new TariffInsertException("cloneAtpData", "CS_RAT_SERVICE_PACKAGE(ATP)", ex);
		}

		// Clone any CS_RAT_SERVICE_PLANS rows mapped to this ATP package, same as
		// is already done for TP packages in ServiceCloneService.cloneService.
		// Zone handling is per-plan inside cloneServicePlans: a new zone is only
		// created when the source plan itself has one.
		List<Long> newPlanIds = serviceCloneService.cloneServicePlans(networkId, atpId, newAtpId, tpName);

		logger.info("ATP service plans cloned oldAtpId={} newAtpId={} newPlanIds={}", atpId, newAtpId, newPlanIds);

		List<Long> bundleIds = jdbcTemplate.queryForList("""
				select BUNDLE_OR_DISCOUNT_ID
				from CS_ATP_ACCUMU_BON_DISC_MAP
				where ATP_ID = ?
				and NETWORK_ID = ?
				""", Long.class, atpId, networkId);

		String firstOldBucketId = null;
		String firstNewBucketId = null;

		// Distinct old bucket zone id -> new zone id, scoped to this cloneAtpData
		// call. Buckets that share the same old zone are mapped to the same new
		// zone; buckets on a different old zone get their own distinct new zone
		// rather than being folded into someone else's zone (which would merge
		// unrelated slab/calendar mappings together).
		Map<Long, Long> bucketZoneIdCache = new HashMap<>();

		// Same idea, but for DATA_ZONE_GROUP_ID — an independent zone reference
		// from ZONE_GROUP_ID, tracked separately.
		Map<Long, Long> bucketDataZoneIdCache = new HashMap<>();

		for (Long oldBundleId : bundleIds) {

			Long newBundleId = cloneBundle(oldBundleId, networkId, tpName);

			cloneImsiRanges(oldBundleId, newBundleId, networkId);

			List<String> bucketIds = jdbcTemplate.queryForList("""
					select BUCKET_ID
					from CS_BNDL_MT_BNDL_BUCKET_MAP
					where BUNDLE_ID = ?
					""", String.class, oldBundleId);

			for (String oldBucketId : bucketIds) {

				String newBucketId = generateNewBucketId();

				// Zone handling is per-bucket: only create + map a zone (CS_RAT_ZONE_GROUPS ->
				// CS_DRE_RATING_GROUP_DETAILS -> calendar -> day types) when the source
				// bucket itself has a ZONE_GROUP_ID. Otherwise the cloned bucket is left
				// with no zone mapped.
				//
				// Distinct old zones get distinct new zones (see bucketZoneIdCache
				// above); buckets sharing the same old zone are mapped to the same
				// new zone. The first distinct old zone encountered in this call
				// uses the specific newBucketZoneId resolved by the caller, so the
				// value reported back still matches what's actually mapped.
				Long oldBucketZoneId = getBucketZoneId(oldBucketId);
				Long newBucketZoneIdForBucket = null;

				if (oldBucketZoneId != null) {

					if (bucketZoneIdCache.containsKey(oldBucketZoneId)) {

						newBucketZoneIdForBucket = bucketZoneIdCache.get(oldBucketZoneId);

						logger.info("Reusing already-cloned zone for bucket oldBucketId={} oldZoneId={} newZoneId={}",
								oldBucketId, oldBucketZoneId, newBucketZoneIdForBucket);
					} else {

						String balanceCategory = getBucketBalanceCategory(oldBucketId);
						ServiceplanZone.ZoneTable zoneTable = servicePlanZone.resolveZoneTableByBalanceCategory(balanceCategory);

						// zoneTable already encodes the VOICE/SMS -> RAT_ZONE_GROUPS vs.
						// DATA -> DRE_RATING_GROUP_DETAILS decision (see
						// resolveZoneTableByBalanceCategory), so the sequence to draw
						// from follows directly from it — no separate branch needed.
						Long candidateZoneId = servicePlanZone.generateNewZoneId(zoneTable);

						boolean cloned = servicePlanZone.cloneZoneIfExists(oldBucketZoneId, candidateZoneId, networkId, tpName, zoneTable);

						if (cloned) {
							newBucketZoneIdForBucket = candidateZoneId;
							bucketZoneIdCache.put(oldBucketZoneId, newBucketZoneIdForBucket);

							logger.info("Zone cloned for bucket oldBucketId={} oldZoneId={} newZoneId={} balanceCategory={} table={}",
									oldBucketId, oldBucketZoneId, newBucketZoneIdForBucket, balanceCategory, zoneTable);
						} else {
							// oldBucketZoneId doesn't actually exist in zoneTable — leave the
							// bucket with no zone rather than pointing it at a new zone id
							// that has no underlying zone data.
							logger.info("Old zone oldZoneId={} not found in {} for bucket oldBucketId={}. Leaving bucket with no zone mapped.",
									oldBucketZoneId, zoneTable, oldBucketId);
						}
					}
				}
				 else {
					logger.info("Bucket oldBucketId={} has no zone mapped. Skipping zone creation.", oldBucketId);
				 }
				 
					
				

				// DATA_ZONE_GROUP_ID is independent of ZONE_GROUP_ID (see
				// getBucketDataZoneGroupId) and always clones into
				// CS_DRE_RATING_GROUP_DETAILS. Tracked with its own cache so it
				// doesn't collide with the ZONE_GROUP_ID cache above.
				Long oldBucketDataZoneGroupId = getBucketDataZoneGroupId(oldBucketId);
				Long newBucketDataZoneGroupId = null;

				if (oldBucketDataZoneGroupId != null) {

					if (bucketDataZoneIdCache.containsKey(oldBucketDataZoneGroupId)) {

						newBucketDataZoneGroupId = bucketDataZoneIdCache.get(oldBucketDataZoneGroupId);

						logger.info("Reusing already-cloned DATA_ZONE_GROUP_ID for bucket oldBucketId={} oldDataZoneGroupId={} newDataZoneGroupId={}",
								oldBucketId, oldBucketDataZoneGroupId, newBucketDataZoneGroupId);
					} else {

						Long candidateDataZoneId = servicePlanZone.generateNewZoneId(ServiceplanZone.ZoneTable.DRE_RATING_GROUP_DETAILS);
						boolean cloned = servicePlanZone.cloneZoneIfExists(oldBucketDataZoneGroupId, candidateDataZoneId, networkId,
								tpName, ServiceplanZone.ZoneTable.DRE_RATING_GROUP_DETAILS);

						if (cloned) {
							newBucketDataZoneGroupId = candidateDataZoneId;
							bucketDataZoneIdCache.put(oldBucketDataZoneGroupId, newBucketDataZoneGroupId);

							logger.info("DATA_ZONE_GROUP_ID cloned for bucket oldBucketId={} oldDataZoneGroupId={} newDataZoneGroupId={}",
									oldBucketId, oldBucketDataZoneGroupId, newBucketDataZoneGroupId);
						} else {
							// oldBucketDataZoneGroupId doesn't actually exist in
							// CS_DRE_RATING_GROUP_DETAILS — leave the bucket with no
							// DATA_ZONE_GROUP_ID rather than a dangling reference.
							logger.info("Old DATA_ZONE_GROUP_ID={} not found for bucket oldBucketId={}. Leaving bucket with no DATA_ZONE_GROUP_ID mapped.",
									oldBucketDataZoneGroupId, oldBucketId);
						}
					}
				} else {
					logger.info("Bucket oldBucketId={} has no DATA_ZONE_GROUP_ID mapped. Skipping.", oldBucketId);
				}

				cloneBucket(oldBucketId, newBucketId, tpName, networkId, newBucketZoneIdForBucket, newBucketDataZoneGroupId);

				try {
					jdbcTemplate.update("""
							insert into CS_BNDL_MT_BNDL_BUCKET_MAP
							(
							    BUNDLE_ID,
							    BUCKET_ID,
							    NETWORK_ID
							)
							values (?,?,?)
							""", newBundleId, newBucketId, networkId);
				} catch (Exception ex) {
					throw new TariffInsertException("cloneAtpData", "CS_BNDL_MT_BNDL_BUCKET_MAP", ex);
				}

				if (firstOldBucketId == null) {
					firstOldBucketId = oldBucketId;
					firstNewBucketId = newBucketId;
				}

				logger.info("Bucket cloned oldBucketId={} newBucketId={} newBucketZoneId={}", oldBucketId, newBucketId,
						newBucketZoneIdForBucket);
			}

			try {
				jdbcTemplate.update("""
						insert into CS_ATP_ACCUMU_BON_DISC_MAP
						(
						    BUNDLE_OR_DISCOUNT_ID,
						    ATP_ID,
						    PLAN_TYPE,
						    NETWORK_ID
						)
						values (?,?,?,?)
						""", newBundleId, newAtpId, "B", networkId);
			} catch (Exception ex) {
				throw new TariffInsertException("cloneAtpData", "CS_ATP_ACCUMU_BON_DISC_MAP", ex);
			}

		}
		copyServiceAtpMap(atpId, newAtpId, networkId);

		logger.info("ATP clone completed oldAtpId={} newAtpId={}", atpId, newAtpId);

		return new CloneAtpResult(newAtpId, firstOldBucketId, firstNewBucketId, newBucketZoneId, newPlanIds);
	}

	private Long cloneBundle(Long oldBundleId, Long networkId, String tpName) {

		Map<String, Object> bundle = jdbcTemplate.queryForMap("""
				select *
				from BNDL_MT_BUNDLE
				where BUNDLE_ID = ?
				""", oldBundleId);

		Long newBundleId = jdbcTemplate.queryForObject("""
				SELECT seq_bundle_id.NEXTVAL FROM DUAL
				""", Long.class);

		try {
			jdbcTemplate.update("""
					INSERT INTO BNDL_MT_BUNDLE
					(
					    BUNDLE_ID,
					    BUNDLE_NAME,
					    BUNDLE_CHARGE,
					    PURCHAGE_TYPE,
					    VALID_FROM,
					    VALID_UPTO,
					    CUSTOMER_GROUP_ID,
					    BUNDLE_STATUS,
					    STATUS_DATE,
					    CREATED_DATE,
					    CREATED_BY,
					    NETWORK_ID,
					    ACTIVATION_NOTIFICATION_TYPE,
					    ACTIVATION_CHARGE,
					    EXPIRY_NOTIFICATION_TYPE,
					    EXPIRY_CHARGE,
					    EXPIRY_NOTIFICATION_THRESHOLD,
					    IMMEDIATE_BENEFIT,
					    DISPLAY_FOR_IVR_BAL_ENQ_YN,
					    BUNDLE_CATEGORY,
					    DEFAULT_BUNDLE_YN,
					    DISPLAY_FOR_USSD_BAL_ENQ_YN,
					    IMSI_FROM,
					    IMSI_TO,
					    TPPKG_ALL_Y_N,
					    PLAN_EXP_NOTIF_THRESHOLD_HRS,
					    ZONE_BASED_VIP_PLAN_FLAG_YN,
					    CHARGE_ID
					)
					VALUES
					(
					    ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSDATE, ?, ?,
					    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
					)
					""",
					newBundleId,
					bundle.get("BUNDLE_NAME").toString().replaceAll("_(CL|TP|ATP)\\d+$", "") + "_" + tpName,
					bundle.get("BUNDLE_CHARGE"),
					bundle.get("PURCHAGE_TYPE"),
					bundle.get("VALID_FROM"),
					bundle.get("VALID_UPTO"),
					bundle.get("CUSTOMER_GROUP_ID"),
					bundle.get("BUNDLE_STATUS"),
					bundle.get("STATUS_DATE"),
					bundle.get("CREATED_BY"),
					networkId,

					bundle.get("ACTIVATION_NOTIFICATION_TYPE"),
					bundle.get("ACTIVATION_CHARGE"),
					bundle.get("EXPIRY_NOTIFICATION_TYPE"),
					bundle.get("EXPIRY_CHARGE"),
					bundle.get("EXPIRY_NOTIFICATION_THRESHOLD"),
					bundle.get("IMMEDIATE_BENEFIT"),
					bundle.get("DISPLAY_FOR_IVR_BAL_ENQ_YN"),
					bundle.get("BUNDLE_CATEGORY"),
					bundle.get("DEFAULT_BUNDLE_YN"),
					bundle.get("DISPLAY_FOR_USSD_BAL_ENQ_YN"),
					bundle.get("IMSI_FROM"),
					bundle.get("IMSI_TO"),
					bundle.get("TPPKG_ALL_Y_N"),
					bundle.get("PLAN_EXP_NOTIF_THRESHOLD_HRS"),
					bundle.get("ZONE_BASED_VIP_PLAN_FLAG_YN"),
					bundle.get("CHARGE_ID"));
		} catch (Exception ex) {
			throw new TariffInsertException("cloneBundle", "BNDL_MT_BUNDLE", ex);
		}

		return newBundleId;
	}

	
	 //Clone all SIM/IMSI range rows for a bundle, assigning them to the new bundle
	
	private void cloneImsiRanges(Long oldBundleId, Long newBundleId, Long networkId) {

		List<Map<String, Object>> ranges = jdbcTemplate.queryForList("""
				select RANGE_FROM, RANGE_TO, SIM_OR_IMSI_FLAG, INCLUDE_EXCLUDE_FLAG
				from BNDL_MT_SIM_IMSI_RANGES
				where BUNDLE_ID = ?
				and NETWORK_ID = ?
				""", oldBundleId, networkId);

		for (Map<String, Object> row : ranges) {

			try {
				jdbcTemplate.update("""
						insert into BNDL_MT_SIM_IMSI_RANGES
						(
						    BUNDLE_ID,
						    RANGE_FROM,
						    RANGE_TO,
						    SIM_OR_IMSI_FLAG,
						    INCLUDE_EXCLUDE_FLAG,
						    NETWORK_ID
						)
						values (?,?,?,?,?,?)
						""",
						newBundleId,
						row.get("RANGE_FROM"),
						row.get("RANGE_TO"),
						row.get("SIM_OR_IMSI_FLAG"),
						row.get("INCLUDE_EXCLUDE_FLAG"),
						networkId);
			} catch (Exception ex) {
				throw new TariffInsertException("cloneImsiRanges", "BNDL_MT_SIM_IMSI_RANGES", ex);
			}
		}

		logger.info("IMSI ranges cloned oldBundleId={} newBundleId={} count={}", oldBundleId, newBundleId,
				ranges.size());
	}

	private String generateNewBucketId() {

		
		Long bucketid= jdbcTemplate.queryForObject(
                                """
                                    SELECT seq_bucket_id.NEXTVAL FROM DUAL
                                                """,
                                Long.class);

			return "T"+bucketid ;

	}

	private void cloneBucket(String oldBucketId, String newBucketId, String tpName, Long networkId,
			Long newBucketZoneId, Long newBucketDataZoneGroupId) {

		try {
			jdbcTemplate.update("""
					insert into BNDL_MT_BUCKETS
					(
					   BUCKET_ID,
					   BUCKET_NAME,
					   BALANCE_CATEGORY,
					   USAGE_TYPE,
					   VALIDITY_PERIOD_DAYS,
					   BUCKET_UNIT_VALUE,
					   BUCKET_UNIT_TYPE,
					   ITERATIVE_BUCKET_YN,
					   ITERATIVE_COUNTS,
					   ROLL_OVER_YN,
					   EXTEND_VALIDITY_YN,
					   BUCKET_STATUS,
					   STATUS_DATE,
					   CREATED_DATE,
					   CREATED_BY,
					   NETWORK_ID,
					   DURATION,
					   UNLIMITED_USAGE_YN,
					   IDD_GROUPID,
					   DAY_TYPE,
					   EXPIRY_NOTIFICATION_TYPE,
					   EXPIRY_NOTIFICATION_THRESHOLD,
					   APLICABLE_FROM_HRS,
					   APLICABLE_TO_HRS,
					   LIMITED_HOURS,
					   BALANCE_ID,
					   LIMITED_NETWORKS_YN,
					   DATA_ZONE_GROUP_ID,
					   ZONE_GROUP_ID,
					   COUNTRY_ISD_PREFIX,
					   PRIORITY,
					   DEVICE_GROUP_ID,
					   LOW_BAL_THRESHOLD1,
					   LOW_BAL_THRESHOLD2,
					   LOW_BAL_THRESHOLD3,
					   LOW_BAL_THRESHOLD4,
					   LOW_BAL_THRESHOLD5,
					   LOW_BAL_THRESHOLD6,
					   LOW_BAL_NOTIF_TYPE1,
					   LOW_BAL_NOTIF_TYPE2,
					   LOW_BAL_NOTIF_TYPE3,
					   LOW_BAL_NOTIF_TYPE4,
					   LOW_BAL_NOTIF_TYPE5,
					   LOW_BAL_NOTIF_TYPE6,
					   LOW_BAL_NOTIF_MSG1,
					   LOW_BAL_NOTIF_MSG2,
					   LOW_BAL_NOTIF_MSG3,
					   LOW_BAL_NOTIF_MSG4,
					   LOW_BAL_NOTIF_MSG5,
					   LOW_BAL_NOTIF_MSG6,
					   LOW_BAL_NOTIF_API1,
					   LOW_BAL_NOTIF_API2,
					   LOW_BAL_NOTIF_API3,
					   LOW_BAL_NOTIF_API4,
					   LOW_BAL_NOTIF_API5,
					   LOW_BAL_NOTIF_API6,
					   LOW_BAL_NOTIF_API_EXT1,
					   LOW_BAL_NOTIF_API_EXT2,
					   LOW_BAL_NOTIF_API_EXT3,
					   LOW_BAL_NOTIF_API_EXT4,
					   LOW_BAL_NOTIF_API_EXT5,
					   LOW_BAL_NOTIF_API_EXT6
					)

					select
					   ?,
					   REGEXP_REPLACE(BUCKET_NAME,'_(CL|TP|ATP)[0-9]+$','') || '_' || ?,
					   BALANCE_CATEGORY,
					   USAGE_TYPE,
					   VALIDITY_PERIOD_DAYS,
					   BUCKET_UNIT_VALUE,
					   BUCKET_UNIT_TYPE,
					   ITERATIVE_BUCKET_YN,
					   ITERATIVE_COUNTS,
					   ROLL_OVER_YN,
					   EXTEND_VALIDITY_YN,
					   BUCKET_STATUS,
					   STATUS_DATE,
					   SYSDATE,
					   CREATED_BY,
					   ?,
					   DURATION,
					   UNLIMITED_USAGE_YN,
					   IDD_GROUPID,
					   DAY_TYPE,
					   EXPIRY_NOTIFICATION_TYPE,
					   EXPIRY_NOTIFICATION_THRESHOLD,
					   APLICABLE_FROM_HRS,
					   APLICABLE_TO_HRS,
					   LIMITED_HOURS,
					   BALANCE_ID,
					   LIMITED_NETWORKS_YN,
					   ?,
					   ?,
					   COUNTRY_ISD_PREFIX,
					   PRIORITY,
					   DEVICE_GROUP_ID,
					   LOW_BAL_THRESHOLD1,
					   LOW_BAL_THRESHOLD2,
					   LOW_BAL_THRESHOLD3,
					   LOW_BAL_THRESHOLD4,
					   LOW_BAL_THRESHOLD5,
					   LOW_BAL_THRESHOLD6,
					   LOW_BAL_NOTIF_TYPE1,
					   LOW_BAL_NOTIF_TYPE2,
					   LOW_BAL_NOTIF_TYPE3,
					   LOW_BAL_NOTIF_TYPE4,
					   LOW_BAL_NOTIF_TYPE5,
					   LOW_BAL_NOTIF_TYPE6,
					   LOW_BAL_NOTIF_MSG1,
					   LOW_BAL_NOTIF_MSG2,
					   LOW_BAL_NOTIF_MSG3,
					   LOW_BAL_NOTIF_MSG4,
					   LOW_BAL_NOTIF_MSG5,
					   LOW_BAL_NOTIF_MSG6,
					   LOW_BAL_NOTIF_API1,
					   LOW_BAL_NOTIF_API2,
					   LOW_BAL_NOTIF_API3,
					   LOW_BAL_NOTIF_API4,
					   LOW_BAL_NOTIF_API5,
					   LOW_BAL_NOTIF_API6,
					   LOW_BAL_NOTIF_API_EXT1,
					   LOW_BAL_NOTIF_API_EXT2,
					   LOW_BAL_NOTIF_API_EXT3,
					   LOW_BAL_NOTIF_API_EXT4,
					   LOW_BAL_NOTIF_API_EXT5,
					   LOW_BAL_NOTIF_API_EXT6

					from BNDL_MT_BUCKETS
					where BUCKET_ID = ?
					""", newBucketId, tpName, networkId, newBucketDataZoneGroupId, newBucketZoneId, oldBucketId);
		} catch (Exception ex) {
			throw new TariffInsertException("cloneBucket", "BNDL_MT_BUCKETS", ex);
		}
	}

	private void copyServiceAtpMap(Long oldAtpId, Long newAtpId, Long networkId) {

		List<Map<String, Object>> serviceMapList = jdbcTemplate.queryForList("""
				select BASIC_SERVICE_ID, DERIVED_SERVICE_ID
				from CS_RAT_SERVICE_ATP_MAP
				where SERVICE_PACKAGE_ID = ?
				and NETWORK_ID = ?
				""", oldAtpId, networkId);

		for (Map<String, Object> row : serviceMapList) {

			jdbcTemplate.update("""
					insert into CS_RAT_SERVICE_ATP_MAP
					(
					    NETWORK_ID,
					    SERVICE_PACKAGE_ID,
					    BASIC_SERVICE_ID,
					    DERIVED_SERVICE_ID
					)
					values (?,?,?,?)
					""", networkId, newAtpId, row.get("BASIC_SERVICE_ID"), row.get("DERIVED_SERVICE_ID"));
		}
	}
}