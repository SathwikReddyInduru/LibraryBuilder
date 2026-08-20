package com.xius.Lb.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xius.Lb.Dto.AtpRequest;
import com.xius.Lb.Dto.BalanceCategoryRequest;
import com.xius.Lb.Dto.ZoneGroupRequest;
import com.xius.Lb.repo.BucketRepository;

@Service
public class BucketService {

	private static final Logger logger = LoggerFactory.getLogger(BucketService.class);

	private final BucketRepository bucketRepository;

	@Value("${bucket.default.iterative-bucket-yn}")
	private String defaultIterativeBucketYn;

	@Value("${bucket.default.iterative-counts}")
	private Integer defaultIterativeCounts;

	@Value("${bucket.default.day-type}")
	private Integer defaultDayType;

	public BucketService( BucketRepository bucketRepository) {
		this.bucketRepository = bucketRepository;
	}

	@Transactional
	public List<String> createBuckets(AtpRequest request) {

		logger.info("Starting bucket creation for ATP name={} networkId={}",
				request != null ? request.getAtpName() : null, request != null ? request.getNetworkId() : null);

		validateRequest(request);

		List<String> bucketIds = new ArrayList<>();

		logger.info("Creating {} bucket(s) for ATP={}", request.getBalanceCategories().size(), request.getAtpName());

		for (BalanceCategoryRequest bucketRequest : request.getBalanceCategories()) {

			logger.info("Processing bucket for balanceCategory={} bucketType={}",
					bucketRequest != null ? bucketRequest.getBalanceCategory() : null,
					bucketRequest != null ? bucketRequest.getBucketType() : null);

			validateBucket(bucketRequest);

			String bucketId = createBucket(request, bucketRequest);

			bucketIds.add(bucketId);

			logger.info("Bucket created successfully bucketId={}", bucketId);
		}

		logger.info("Bucket creation completed successfully for ATP={}. Created bucketIds={}", request.getAtpName(),
				bucketIds);

		return bucketIds;
	}

	/**
	 * Public entry point used by Modify ATP to create exactly one new bucket
	 * for a newly-added balance category, reusing the same validation +
	 * insert logic as normal ATP creation ({@link #createBuckets}).
	 */
	@Transactional
	public String createSingleBucket(AtpRequest request, BalanceCategoryRequest bucketRequest) {

		logger.info("Creating single bucket for Modify ATP balanceCategory={} ATP={}",
				bucketRequest != null ? bucketRequest.getBalanceCategory() : null,
				request != null ? request.getAtpName() : null);

		validateBucket(bucketRequest);

		String bucketId = createBucket(request, bucketRequest);

		logger.info("Single bucket created for Modify ATP bucketId={}", bucketId);

		return bucketId;
	}

	/**
	 * Updates an existing bucket in place for Modify ATP. Only field values
	 * are re-written - the bucket row, and everything hanging under it, is
	 * never dropped/recreated. Roaming networks (no natural key) are
	 * resynced by delete-then-reinsert.
	 */
	@Transactional
	public void updateBucket(AtpRequest request, BalanceCategoryRequest bucketRequest,
			BalanceCategoryRequest existingBucket) {

		validateBucket(bucketRequest);

		String bucketId = bucketRequest.getBucketId();

		if (isBlank(bucketId)) {

			logger.error("Validation failed: bucketId is mandatory to update an existing bucket");

			throw new IllegalArgumentException("bucketId is mandatory to update an existing bucket");
		}

		String balanceCategory = bucketRequest.getBalanceCategory().trim().toUpperCase();

		logger.info("Updating bucketId={} balanceCategory={} for ATP={}", bucketId, balanceCategory,
				request.getAtpName());

		Long usageType = calculateUsageType(bucketRequest.getUsageType());

		String limitedNetworksYn = determineLimitedNetworks(request.getRoamingNetworks());

		String limitedHours = determineLimitedHours(request.getApplicableFromHrs(), request.getApplicableToHrs());

		Long zoneGroupId = getZoneGroupIdWithRatingN(request.getZoneGroup());

		Long dataZoneGroupId = getZoneGroupIdWithRatingN(request.getDataZoneGroupId());

		// bucketUnitType (Unit Type) and unlimitedUsageYn (Unlimited Usage) are
		// locked in the UI for an existing bucket (see
		// Atpcreate.js#applyEditModeFieldLocks) — enforced here too, by
		// ignoring whatever the request sent for them and always persisting
		// what's already stored, so a direct API call can't change them
		// either. Falls back to the request's values only if there's
		// somehow no existing entry to compare against.
		String bucketUnitType = existingBucket != null ? existingBucket.getBucketUnitType()
				: bucketRequest.getBucketUnitType();

		String unlimitedUsageYn = existingBucket != null ? existingBucket.getUnlimitedUsageYn()
				: bucketRequest.getUnlimitedUsageYn();

		try {

			bucketRepository.updateBucket(bucketId, balanceCategory, usageType, request.getValidityPeriodDays(),
					bucketRequest.getBucketUnitValue(), bucketUnitType, request.getRollOverYn(),
					request.getExtendValidityYn(), zoneGroupId, dataZoneGroupId, limitedNetworksYn, limitedHours,
					request.getApplicableFromHrs(), request.getApplicableToHrs(), unlimitedUsageYn);

			logger.info("Successfully updated bucketId={}", bucketId);

		} catch (Exception ex) {

			logger.error("Error updating bucketId={} balanceCategory={}", bucketId, balanceCategory, ex);

			throw ex;
		}

		bucketRepository.deleteBucketRoamingNetworks(bucketId);

		insertRoamingNetworks(request, bucketId);

		logger.info("Completed bucket update bucketId={}", bucketId);
	}

	private String createBucket(AtpRequest request, BalanceCategoryRequest bucketRequest) {

		String balanceCategory = bucketRequest.getBalanceCategory().trim().toUpperCase();

		String bucketType = bucketRequest.getBucketType().trim().toUpperCase();

		logger.info("Starting bucket creation balanceCategory={} bucketType={} networkId={}", balanceCategory,
				bucketType, request.getNetworkId());

		Long sequence = bucketRepository.getNextBucketSequence();

		logger.info("Generated bucket sequence={} for balanceCategory={}", sequence, balanceCategory);

		String bucketId = bucketType + sequence;

		String bucketName = generateBucketName(request.getAtpName(), balanceCategory, sequence);

		logger.info("Generated bucketId={} bucketName={}", bucketId, bucketName);

		Long usageType = calculateUsageType(bucketRequest.getUsageType());

		logger.info("Calculated usageType={} for bucketId={}", usageType, bucketId);

		String iterativeBucketYn = defaultIterativeBucketYn;

		Integer iterativeCounts = defaultIterativeCounts;

		validateIterativeValues(iterativeBucketYn, iterativeCounts);

		logger.info("Iterative configuration for bucketId={} iterativeBucketYn={} iterativeCounts={}", bucketId,
				iterativeBucketYn, iterativeCounts);

		String limitedNetworksYn = determineLimitedNetworks(request.getRoamingNetworks());

		String limitedHours = determineLimitedHours(request.getApplicableFromHrs(), request.getApplicableToHrs());

		logger.info("Bucket restrictions bucketId={} limitedNetworksYn={} limitedHours={}", bucketId, limitedNetworksYn,
				limitedHours);

		Integer priority = null;
		Long deviceGroupId = null;
		Integer dayType = defaultDayType;

		Long zoneGroupId = getZoneGroupIdWithRatingN(request.getZoneGroup());

		Long dataZoneGroupId = getZoneGroupIdWithRatingN(request.getDataZoneGroupId());

		logger.info("Resolved bucket mappings bucketId={} zoneGroupId={} dataZoneGroupId={} dayType={}", bucketId,
				zoneGroupId, dataZoneGroupId, dayType);

		try {

			bucketRepository.insertBucket(bucketId, bucketName, balanceCategory, usageType,
					request.getValidityPeriodDays(), bucketRequest.getBucketUnitValue(),
					bucketRequest.getBucketUnitType(), iterativeBucketYn, iterativeCounts, request.getRollOverYn(),
					request.getExtendValidityYn(), request.getCreatedBy(), request.getNetworkId(), null, zoneGroupId,
					dataZoneGroupId, limitedNetworksYn, limitedHours, request.getApplicableFromHrs(),
					request.getApplicableToHrs(), priority, deviceGroupId, bucketRequest.getUnlimitedUsageYn(),
					dayType);

			logger.info("Successfully inserted bucket into database bucketId={}", bucketId);

		} catch (Exception ex) {

			logger.error("Error inserting bucket bucketId={} balanceCategory={} networkId={}", bucketId,
					balanceCategory, request.getNetworkId(), ex);

			throw ex;
		}

		insertRoamingNetworks(request, bucketId);

		logger.info("Completed bucket creation bucketId={}", bucketId);

		return bucketId;
	}

	private void insertRoamingNetworks(AtpRequest request, String bucketId) {

		List<String> roamingNetworks = request.getRoamingNetworks();

		if (roamingNetworks == null || roamingNetworks.isEmpty()) {

			logger.info("No roaming networks configured for bucketId={}", bucketId);

			return;
		}

		if (roamingNetworks.size() == 1 && "ALL".equalsIgnoreCase(roamingNetworks.get(0))) {

			logger.info(
					"Roaming network configured as ALL. Skipping individual roaming network mapping for bucketId={}",
					bucketId);

			return;
		}

		logger.info("Processing {} roaming network(s) for bucketId={}", roamingNetworks.size(), bucketId);

		for (String roamingNetwork : roamingNetworks) {

			if (roamingNetwork == null || roamingNetwork.isBlank()) {

				logger.warn("Skipping blank roaming network for bucketId={}", bucketId);

				continue;
			}

			Long networkId;

			try {

				networkId = Long.valueOf(roamingNetwork.trim());

			} catch (NumberFormatException ex) {

				logger.error("Invalid roaming network ID={} for bucketId={}", roamingNetwork, bucketId, ex);

				throw new IllegalArgumentException("Invalid roaming network ID: " + roamingNetwork);
			}

			logger.info("Inserting roaming network mapping bucketId={} roamingNetworkId={}", bucketId, networkId);

			try {

				bucketRepository.insertBucketRoamingNetwork(request.getNetworkId(), bucketId, networkId);

				logger.info("Successfully inserted roaming network mapping bucketId={} roamingNetworkId={}", bucketId,
						networkId);

			} catch (Exception ex) {

				logger.error("Error inserting roaming network mapping bucketId={} roamingNetworkId={}", bucketId,
						networkId, ex);

				throw ex;
			}
		}
	}

	private void validateRequest(AtpRequest request) {

		logger.info("Validating bucket creation request");

		if (request == null) {

			logger.error("Bucket creation request is null");

			throw new IllegalArgumentException("Request cannot be null");
		}

		logger.info("Validating request for ATP={} networkId={}", request.getAtpName(), request.getNetworkId());

		if (request.getNetworkId() == null) {

			logger.error("Validation failed: networkId is missing");

			throw new IllegalArgumentException("networkId is mandatory");
		}

		if (isBlank(request.getCreatedBy())) {

			logger.error("Validation failed: createdBy is missing");

			throw new IllegalArgumentException("createdBy is mandatory");
		}

		if (isBlank(request.getAtpName())) {

			logger.error("Validation failed: atpName is missing");

			throw new IllegalArgumentException("atpName is mandatory");
		}

		if (request.getBalanceCategories() == null || request.getBalanceCategories().isEmpty()) {

			logger.error("Validation failed: no balance categories supplied for ATP={}", request.getAtpName());

			throw new IllegalArgumentException("At least one balance category is required");
		}

		if (request.getValidityPeriodDays() == null || request.getValidityPeriodDays() <= 0) {

			logger.error("Validation failed: invalid validityPeriodDays={} for ATP={}", request.getValidityPeriodDays(),
					request.getAtpName());

			throw new IllegalArgumentException("validityPeriodDays must be greater than 0");
		}

		validateYn(request.getRollOverYn(), "rollOverYn");

		validateYn(request.getExtendValidityYn(), "extendValidityYn");

		if (request.getApplicableFromHrs() != null && request.getApplicableToHrs() != null
				&& request.getApplicableFromHrs() > request.getApplicableToHrs()) {

			logger.error("Validation failed: applicableFromHrs={} is greater than applicableToHrs={}",
					request.getApplicableFromHrs(), request.getApplicableToHrs());

			throw new IllegalArgumentException("applicableFromHrs cannot be greater than applicableToHrs");
		}

		logger.info("Bucket creation request validation completed successfully for ATP={}", request.getAtpName());
	}

	private void validateBucket(BalanceCategoryRequest bucket) {

		logger.info("Validating bucket balanceCategory={} bucketType={}",
				bucket != null ? bucket.getBalanceCategory() : null, bucket != null ? bucket.getBucketType() : null);

		if (bucket == null) {

			logger.error("Validation failed: balance category is null");

			throw new IllegalArgumentException("Balance category cannot be null");
		}

		if (isBlank(bucket.getBalanceCategory())) {

			logger.error("Validation failed: balanceCategory is missing");

			throw new IllegalArgumentException("balanceCategory is mandatory");
		}

		String category = bucket.getBalanceCategory().trim().toUpperCase();

		if (!List.of("SMS", "MMS", "VOICE", "DATA", "GLOBAL").contains(category)) {

			logger.error("Validation failed: unsupported balanceCategory={}", category);

			throw new IllegalArgumentException("Balance category does not match: " + category);
		}

		if (isBlank(bucket.getBucketType())) {

			logger.error("Validation failed: bucketType is missing for category={}", category);

			throw new IllegalArgumentException("bucketType is mandatory");
		}

		if (bucket.getUsageType() == null || bucket.getUsageType().isEmpty()) {

			logger.error("Validation failed: usageType is missing for category={}", category);

			throw new IllegalArgumentException("usageType is mandatory");
		}

		if (bucket.getBucketUnitValue() == null || bucket.getBucketUnitValue() == 0) {

			logger.error("Validation failed: bucketUnitValue={} for category={}", bucket.getBucketUnitValue(),
					category);

			throw new IllegalArgumentException("Bucket Unit Value must be more than 0");
		}

		if (isBlank(bucket.getBucketUnitType())) {

			logger.error("Validation failed: bucketUnitType is missing for category={}", category);

			throw new IllegalArgumentException("bucketUnitType is mandatory");
		}

		validateUnitType(category, bucket.getBucketUnitType());

		validateYn(bucket.getUnlimitedUsageYn(), "unlimitedUsageYn");

		for (Long usageType : bucket.getUsageType()) {

			if (usageType == null) {

				logger.error("Validation failed: usageType contains null for category={}", category);

				throw new IllegalArgumentException("usageType cannot contain null");
			}
		}

		logger.info("Bucket validation completed successfully balanceCategory={} bucketType={}", category,
				bucket.getBucketType());
	}

	private void validateUnitType(String category, String unitType) {

		String unit = unitType.trim().toUpperCase();

		logger.info("Validating bucket unit type category={} unitType={}", category, unit);

		switch (category) {

		case "VOICE":

			if (!List.of("SEC", "AMT", "CALLS").contains(unit)) {

				logger.error("Invalid unitType={} for VOICE balance category", unit);

				throw new IllegalArgumentException("Balance category Vs bucket unit type does not match.");
			}

			break;

		case "SMS":
		case "MMS":

			if (!List.of("MSG", "AMT").contains(unit)) {

				logger.error("Invalid unitType={} for {} balance category", unit, category);

				throw new IllegalArgumentException("Balance category Vs bucket unit type does not match.");
			}

			break;

		case "DATA":

			if (!List.of("BYT", "AMT").contains(unit)) {

				logger.error("Invalid unitType={} for DATA balance category", unit);

				throw new IllegalArgumentException("Balance category Vs bucket unit type does not match.");
			}

			break;

		case "GLOBAL":

			if (!"AMT".equals(unit)) {

				logger.error("Invalid unitType={} for GLOBAL balance category", unit);

				throw new IllegalArgumentException("Balance category Vs bucket unit type does not match.");
			}

			break;

		default:

			logger.error("Unsupported balance category={} while validating unitType={}", category, unit);

			throw new IllegalArgumentException("Unsupported balance category: " + category);
		}

		logger.info("Bucket unit type validation successful category={} unitType={}", category, unit);
	}

	private Long calculateUsageType(List<Long> usageTypes) {

		logger.info("Calculating usageType from usageTypes={}", usageTypes);

		long result = 0;

		for (Long usageType : usageTypes) {

			result += usageType;
		}

		logger.info("Calculated usageType={} from usageTypes={}", result, usageTypes);

		return result;
	}

	private String determineLimitedNetworks(List<String> roamingNetworks) {

		if (roamingNetworks == null || roamingNetworks.isEmpty()) {

			logger.info("No roaming networks found. limitedNetworksYn=N");

			return "N";
		}

		if (roamingNetworks.size() == 1 && "ALL".equalsIgnoreCase(roamingNetworks.get(0))) {

			logger.info("Roaming network is ALL. limitedNetworksYn=N");

			return "N";
		}

		logger.info("Specific roaming networks configured. limitedNetworksYn=Y");

		return "Y";
	}

	private String determineLimitedHours(Integer applicableFromHrs, Integer applicableToHrs) {

		if (applicableFromHrs == null && applicableToHrs == null) {

			logger.info("No applicable hour restriction. limitedHours=N");

			return "N";
		}

		logger.info("Applicable hour restriction configured from={} to={}. limitedHours=Y", applicableFromHrs,
				applicableToHrs);

		return "Y";
	}

	private void validateIterativeValues(String iterativeBucketYn, Integer iterativeCounts) {

		logger.info("Validating iterative values iterativeBucketYn={} iterativeCounts={}", iterativeBucketYn,
				iterativeCounts);

		if (!"Y".equalsIgnoreCase(iterativeBucketYn) && !"N".equalsIgnoreCase(iterativeBucketYn)) {

			logger.error("Invalid iterativeBucketYn={}", iterativeBucketYn);

			throw new IllegalArgumentException("Iterative Bucket MUST be Y or N.");
		}

		if (iterativeCounts == null) {

			logger.error("iterativeCounts is null");

			throw new IllegalArgumentException("iterativeCounts cannot be null");
		}

		if ("Y".equalsIgnoreCase(iterativeBucketYn) && iterativeCounts == 0) {

			logger.error("Invalid iterativeCounts={} when iterativeBucketYn=Y", iterativeCounts);

			throw new IllegalArgumentException("Iterative counts must be MORE than 0.");
		}

		if ("N".equalsIgnoreCase(iterativeBucketYn) && iterativeCounts > 0) {

			logger.error("Invalid iterativeCounts={} when iterativeBucketYn=N", iterativeCounts);

			throw new IllegalArgumentException("Iterative counts must be EQUAL to 0.");
		}

		logger.info("Iterative values validated successfully");
	}

	private String generateBucketName(String atpName, String balanceCategory, Long sequence) {

		String bucketName = atpName + "_" + balanceCategory + "_" + sequence;

		logger.info("Generated bucket name={} for ATP={} balanceCategory={} sequence={}", bucketName, atpName,
				balanceCategory, sequence);

		return bucketName;
	}

	private void validateYn(String value, String fieldName) {

		if (value == null) {

			logger.info("{} is null. Skipping Y/N validation", fieldName);

			return;
		}

		if (!"Y".equalsIgnoreCase(value) && !"N".equalsIgnoreCase(value)) {

			logger.error("Invalid Y/N value fieldName={} value={}", fieldName, value);

			throw new IllegalArgumentException(fieldName + " must be Y or N");
		}

		logger.info("Y/N validation successful fieldName={} value={}", fieldName, value);
	}

	private boolean isBlank(String value) {

		return value == null || value.trim().isEmpty();
	}

	private Long getZoneGroupIdWithRatingN(List<ZoneGroupRequest> zoneGroups) {

		if (zoneGroups == null || zoneGroups.isEmpty()) {

			logger.info("No zone groups supplied");

			return null;
		}

		Long zoneGroupId = zoneGroups.stream().filter(z -> z != null)
				.filter(z -> "N".equalsIgnoreCase(z.getRatingFlag())).map(ZoneGroupRequest::getId)
				.filter(id -> id != null).findFirst().orElse(null);

		logger.info("Resolved zoneGroupId={} from {} zone group(s) with ratingFlag=N", zoneGroupId, zoneGroups.size());

		return zoneGroupId;
	}
}