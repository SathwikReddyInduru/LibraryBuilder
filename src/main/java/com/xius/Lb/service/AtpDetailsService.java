package com.xius.Lb.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.xius.Lb.Dto.AtpDetailsResponse;
import com.xius.Lb.Dto.BalanceCategoryRequest;
import com.xius.Lb.Dto.ZoneGroupRequest;
import com.xius.Lb.repo.AtpDetailsRepository;
import com.xius.Lb.repo.AtpDetailsRepository.AtpCore;
import com.xius.Lb.repo.AtpDetailsRepository.BucketRow;
import com.xius.Lb.repo.AtpDetailsRepository.BundleCore;
import com.xius.Lb.repo.AtpDetailsRepository.ServicePlanRow;
import com.xius.Lb.repo.AtpDetailsRepository.SimRangeRow;

@Service
public class AtpDetailsService {

	private static final Logger logger = LoggerFactory.getLogger(AtpDetailsService.class);

	// service_plan_type_id -> balance category, matching
	// ServicePlanService1#resolveTypeOfService
	private static final int TYPE_OF_SERVICE_VOICE = 1;
	private static final int TYPE_OF_SERVICE_SMS = 2;
	private static final int TYPE_OF_SERVICE_DATA = 3;

	private final AtpDetailsRepository atpDetailsRepository;

	public AtpDetailsService(AtpDetailsRepository atpDetailsRepository) {
		this.atpDetailsRepository = atpDetailsRepository;
	}

	public AtpDetailsResponse getAtpDetails(Long atpId) {

		logger.info("Fetching ATP details atpId={}", atpId);

		if (atpId == null) {
			throw new IllegalArgumentException("atpId is mandatory");
		}

		AtpCore atpCore = atpDetailsRepository.findAtpCore(atpId);

		if (atpCore == null) {
			logger.error("ATP not found atpId={}", atpId);
			throw new IllegalArgumentException("ATP not found: " + atpId);
		}

		AtpDetailsResponse response = new AtpDetailsResponse();

		response.setNetworkId(atpCore.networkId());
		response.setAtpName(atpCore.atpName());
		response.setCategoryOfferCode(atpCore.categoryOfferCode());
		response.setDescription(atpCore.description());
		response.setPublicityId(atpCore.publicityId());
		response.setValidTo(atpCore.validTo());

		// Not persisted anywhere by the current insert flow: this endpoint only
		// ever creates Rating (VOICE/SMS/DATA) service plans, never Billing
		// plans, so there is no billing service plan to read a value back from.
		response.setBillingServiceType(null);

		Long bundleId = atpDetailsRepository.findBundleIdForAtp(atpId);

		List<String> bucketIds = new ArrayList<>();

		if (bundleId != null) {

			BundleCore bundle = atpDetailsRepository.findBundle(bundleId);

			if (bundle != null) {
				response.setValidFrom(bundle.validFrom());
				response.setCreatedBy(bundle.createdBy());
				response.setVipPlanFlagYn(bundle.vipPlanFlagYn());
			}

			bucketIds = atpDetailsRepository.findBucketIdsForBundle(bundleId);

			List<SimRangeRow> simRanges = atpDetailsRepository.findSimRanges(bundleId);

			response.setSimRangeDetails(buildSimRangeDetails(simRanges));
			response.setSimImsiFlag(simRanges.isEmpty() ? null : simRanges.get(0).simImsiFlag());
		} else {
			logger.warn("No bundle mapping found for atpId={}", atpId);
		}

		List<BucketRow> buckets = atpDetailsRepository.findBuckets(bucketIds);

		BucketDerivedZoneIds bucketZoneIds = applyBucketDerivedFields(response, buckets);

		response.setRoamingNetworks(atpDetailsRepository.findRoamingNetworksForBuckets(bucketIds));

		response.setDerivedServiceSelections(atpDetailsRepository.findDerivedServiceSelections(atpId));

		List<Long> servicePlanIds = atpDetailsRepository.findServicePlanIdsForAtp(atpId);

		List<ServicePlanRow> servicePlans = atpDetailsRepository.findServicePlans(servicePlanIds);

		applyServicePlanDerivedFields(response, servicePlans, bucketZoneIds);

		logger.info("Completed fetching ATP details atpId={}", atpId);

		return response;
	}

	// =================================================================
	// BUCKET -> RESPONSE FIELDS
	// =================================================================

	private BucketDerivedZoneIds applyBucketDerivedFields(AtpDetailsResponse response, List<BucketRow> buckets) {

		List<BalanceCategoryRequest> balanceCategories = new ArrayList<>();

		Long dataZoneGroupIdN = null;
		Long voiceOrSmsZoneGroupIdN = null;
		boolean validityPeriodSeen = false;

		for (BucketRow bucket : buckets) {

			BalanceCategoryRequest balanceCategory = new BalanceCategoryRequest();

			balanceCategory.setBalanceCategory(bucket.balanceCategory());
			balanceCategory.setBucketType(extractBucketType(bucket.bucketId()));
			balanceCategory.setUsageType(decomposeUsageType(bucket.usageType()));
			balanceCategory.setBucketUnitType(bucket.bucketUnitType());
			balanceCategory.setBucketUnitValue(bucket.bucketUnitValue());
			balanceCategory.setUnlimitedUsageYn(bucket.unlimitedUsageYn());

			balanceCategories.add(balanceCategory);

			// Every bucket for an ATP is created with the same
			// validityPeriodDays / rollOverYn / extendValidityYn /
			// applicableFromHrs / applicableToHrs (see BucketService#createBucket),
			// so the first bucket's values represent the whole ATP.
			if (!validityPeriodSeen) {

				// BucketRepository#insertBucket does NVL(?, -1) for
				// validity_period_days, so -1 on the way in means the user
				// picked "Unlimited" on the create form and no days value was
				// sent at all (see Atpcreate.js validityPeriodType handling).
				Integer validityPeriodDays = bucket.validityPeriodDays();

				if (validityPeriodDays != null && validityPeriodDays == -1) {
					response.setValidityPeriodType("UNLIMITED");
					response.setValidityPeriodDays(null);
				} else {
					response.setValidityPeriodType("LIMITED");
					response.setValidityPeriodDays(validityPeriodDays);
				}

				response.setRollOverYn(bucket.rollOverYn());
				response.setExtendValidityYn(bucket.extendValidityYn());
				response.setApplicableFromHrs(bucket.applicableFromHrs());
				response.setApplicableToHrs(bucket.applicableToHrs());

				validityPeriodSeen = true;
			}

			if ("DATA".equalsIgnoreCase(bucket.balanceCategory()) && bucket.dataZoneGroupId() != null) {
				dataZoneGroupIdN = bucket.dataZoneGroupId();
			}

			if (("VOICE".equalsIgnoreCase(bucket.balanceCategory()) || "SMS".equalsIgnoreCase(bucket.balanceCategory()))
					&& bucket.zoneGroupId() != null && voiceOrSmsZoneGroupIdN == null) {
				voiceOrSmsZoneGroupIdN = bucket.zoneGroupId();
			}
		}

		response.setBalanceCategories(balanceCategories);

		return new BucketDerivedZoneIds(voiceOrSmsZoneGroupIdN, dataZoneGroupIdN);
	}

	/**
	 * Carries the ratingFlag=N zone group ids resolved while walking the bucket
	 * rows, so they can be merged with the ratingFlag=Y ids resolved while
	 * walking the service plan rows. Kept as a local value (not instance state)
	 * because AtpDetailsService is a singleton Spring bean shared across
	 * concurrent requests.
	 */
	private record BucketDerivedZoneIds(Long voiceOrSmsZoneGroupIdN, Long dataZoneGroupIdN) {
	}

	// =================================================================
	// SERVICE PLAN -> RESPONSE FIELDS
	// =================================================================

	private void applyServicePlanDerivedFields(AtpDetailsResponse response, List<ServicePlanRow> servicePlans,
			BucketDerivedZoneIds bucketZoneIds) {

		Long zoneGroupIdY = null;
		Long dataServicePlanId = null;

		for (ServicePlanRow plan : servicePlans) {

			if (response.getTypeOfService() == null) {
				response.setTypeOfService(mapServicePlanTypeToTypeOfService(plan.servicePlanType()));
			}

			if (response.getRatingType() == null) {
				response.setRatingType(plan.ratingType());
			}

			if (response.getAllowNationalRoamingData() == null) {
				response.setAllowNationalRoamingData(plan.allowNationalRoamingData());
			}

			if (response.getAllowInternationalRoamingData() == null) {
				response.setAllowInternationalRoamingData(plan.allowInternationalRoamingData());
			}

			if (response.getCalendarConfig() == null) {
				response.setCalendarConfig(plan.calendarConfig());
			}

			if (plan.typeOfService() != null) {

				if ((plan.typeOfService() == TYPE_OF_SERVICE_VOICE || plan.typeOfService() == TYPE_OF_SERVICE_SMS)
						&& plan.zoneGroupId() != null && zoneGroupIdY == null) {
					zoneGroupIdY = plan.zoneGroupId();
				}

				if (plan.typeOfService() == TYPE_OF_SERVICE_DATA) {
					dataServicePlanId = plan.servicePlanId();
				}
			}
		}

		response.setZoneGroup(buildZoneGroupList(zoneGroupIdY, bucketZoneIds.voiceOrSmsZoneGroupIdN()));

		List<Long> dataZoneIdsY = atpDetailsRepository.findDataZoneMappings(dataServicePlanId);

		response.setDataZoneGroupId(buildDataZoneGroupList(dataZoneIdsY, bucketZoneIds.dataZoneGroupIdN()));
	}

	// =================================================================
	// HELPERS
	// =================================================================

	/**
	 * cs_rat_service_plans.SERVICE_PLAN_TYPE stores 'R' / 'B', which the create
	 * form's Type of Service dropdown represents as 1 ("Rating") / 2 ("Billing")
	 * — see the typeOfServiceLabel map in Atpcreate.js. This is unrelated to the
	 * per-plan numeric TYPE_OF_SERVICE column (1/2/3 for VOICE/SMS/DATA), which
	 * is handled separately for zoneGroup/dataZoneGroupId resolution.
	 */
	private Integer mapServicePlanTypeToTypeOfService(String servicePlanType) {

		if (servicePlanType == null) {
			return null;
		}

		return switch (servicePlanType.trim().toUpperCase()) {
		case "R" -> 1;
		case "B" -> 2;
		default -> null;
		};
	}

	private List<String> buildSimRangeDetails(List<SimRangeRow> simRanges) {

		List<String> ranges = new ArrayList<>();

		for (SimRangeRow row : simRanges) {
			ranges.add(row.includeExcludeFlag() + "-" + row.rangeFrom() + "-" + row.rangeTo());
		}

		return ranges;
	}

	private String extractBucketType(String bucketId) {

		if (bucketId == null) {
			return null;
		}

		// Bucket IDs are generated as bucketType + sequence (see
		// BucketService#createBucket), e.g. "T12345" -> bucketType "T".
		return bucketId.replaceAll("\\d+$", "");
	}

	/**
	 * BucketService#calculateUsageType sums the requested usageType list into a
	 * single value. Since every value in that list is a distinct bit flag (1, 2,
	 * 4, 8, 16, ...), the sum can be decomposed back into the original flags by
	 * bit position.
	 */
	private List<Integer> decomposeUsageType(Long usageType) {

		List<Integer> flags = new ArrayList<>();

		if (usageType == null) {
			return flags;
		}

		long remaining = usageType;

		for (int bit = 0; bit < 32 && remaining != 0; bit++) {

			long flag = 1L << bit;

			if ((remaining & flag) != 0) {
				flags.add((int) flag);
				remaining &= ~flag;
			}
		}

		return flags;
	}

	private List<ZoneGroupRequest> buildZoneGroupList(Long ratingY, Long ratingN) {

		List<ZoneGroupRequest> zoneGroups = new ArrayList<>();

		if (ratingY != null) {
			zoneGroups.add(zoneGroupEntry(ratingY, "Y"));
		}

		if (ratingN != null) {
			zoneGroups.add(zoneGroupEntry(ratingN, "N"));
		}

		return zoneGroups;
	}

	private List<ZoneGroupRequest> buildDataZoneGroupList(List<Long> ratingYIds, Long ratingN) {

		List<ZoneGroupRequest> zoneGroups = new ArrayList<>();

		for (Long id : ratingYIds) {
			zoneGroups.add(zoneGroupEntry(id, "Y"));
		}

		if (ratingN != null) {
			zoneGroups.add(zoneGroupEntry(ratingN, "N"));
		}

		return zoneGroups;
	}

	private ZoneGroupRequest zoneGroupEntry(Long id, String ratingFlag) {

		ZoneGroupRequest zoneGroup = new ZoneGroupRequest();

		zoneGroup.setId(id);
		zoneGroup.setRatingFlag(ratingFlag);

		return zoneGroup;
	}
}