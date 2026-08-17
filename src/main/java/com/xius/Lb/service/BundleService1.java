package com.xius.Lb.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xius.Lb.Dto.AtpRequest;
import com.xius.Lb.repo.BundleRepository;

@Service
public class BundleService1 {

	private static final Logger logger = LoggerFactory.getLogger(BundleService1.class);

	private final BundleRepository bundleRepository;

	@Value("${bundle.default.plan-exp-notif-threshold-hrs}")
	private Integer defaultPlanExpNotifThresholdHrs;

	public BundleService1(BundleRepository bundleRepository) {
		this.bundleRepository = bundleRepository;
	}

	@Transactional
	public Long createBundle(AtpRequest request, List<String> bucketIds) {

		logger.info("Starting Bundle creation for ATP={} networkId={}", request != null ? request.getAtpName() : null,
				request != null ? request.getNetworkId() : null);

		validateRequest(request);
		validateBucketIds(bucketIds);

		logger.info("Bundle request validation completed successfully for ATP={}", request.getAtpName());

		logger.info("Creating Bundle using {} bucket(s) for ATP={}", bucketIds.size(), request.getAtpName());

		Long bundleId = bundleRepository.getNextBundleId();

		logger.info("Generated bundleId={} for ATP={}", bundleId, request.getAtpName());

		String bundleName = generateBundleName(request.getAtpName(), bundleId);

		logger.info("Generated bundleName={} for bundleId={}", bundleName, bundleId);

		try {

			bundleRepository.insertBundle(bundleId, bundleName, request.getValidFrom(), request.getValidTo(), null,
					request.getCreatedBy(), request.getNetworkId(), null, null, null, null, null, null, null,
					defaultPlanExpNotifThresholdHrs, request.getVipPlanFlagYn());

			logger.info("Successfully inserted Bundle bundleId={} bundleName={} networkId={}", bundleId, bundleName,
					request.getNetworkId());

		} catch (Exception ex) {

			logger.error("Error inserting Bundle bundleId={} bundleName={} networkId={}", bundleId, bundleName,
					request.getNetworkId(), ex);

			throw ex;
		}

		mapBucketsToBundle(bundleId, request.getNetworkId(), bucketIds);

		insertSimImsiRanges(bundleId, request);

		logger.info("Bundle creation completed successfully bundleId={} ATP={}", bundleId, request.getAtpName());

		return bundleId;
	}

	private void mapBucketsToBundle(Long bundleId, Long networkId, List<String> bucketIds) {

		logger.info("Starting bucket mapping for bundleId={} networkId={} bucketCount={}", bundleId, networkId,
				bucketIds.size());

		for (String bucketId : bucketIds) {

			if (bucketId == null || bucketId.isBlank()) {

				logger.error("Invalid bucket ID found while mapping to bundleId={}", bundleId);

				throw new IllegalArgumentException("Bucket ID cannot be null or empty");
			}

			logger.info("Mapping bucketId={} to bundleId={} networkId={}", bucketId, bundleId, networkId);

			try {

				bundleRepository.insertBundleBucketMapping(bundleId, bucketId, networkId);

				logger.info("Successfully mapped bucketId={} to bundleId={}", bucketId, bundleId);

			} catch (Exception ex) {

				logger.error("Error mapping bucketId={} to bundleId={} networkId={}", bucketId, bundleId, networkId,
						ex);

				throw ex;
			}
		}

		logger.info("Completed bucket mapping for bundleId={} mappedBucketCount={}", bundleId, bucketIds.size());
	}

	private void insertSimImsiRanges(Long bundleId, AtpRequest request) {

		List<String> simRanges = request.getSimRangeDetails();

		if (simRanges == null || simRanges.isEmpty()) {

			logger.info("No SIM/IMSI ranges configured for bundleId={}", bundleId);

			return;
		}

		logger.info("Starting SIM/IMSI range insertion for bundleId={} rangeCount={}", bundleId, simRanges.size());

		for (String simRange : simRanges) {

			logger.info("Processing SIM/IMSI range for bundleId={}", bundleId);

			validateSimRange(simRange);

			String[] values = simRange.split("-", -1);

			try {

				bundleRepository.insertSimImsiRange(bundleId, values[0], request.getSimImsiFlag(), values[1], values[2],
						request.getNetworkId());

				logger.info("Successfully inserted SIM/IMSI range for bundleId={} rangeFrom={} rangeTo={}", bundleId,
						values[1], values[2]);

			} catch (Exception ex) {

				logger.error("Error inserting SIM/IMSI range for bundleId={} rangeFrom={} rangeTo={}", bundleId,
						values[1], values[2], ex);

				throw ex;
			}
		}

		logger.info("Completed SIM/IMSI range insertion for bundleId={}", bundleId);
	}

	private String generateBundleName(String atpName, Long bundleId) {

		String bundleName = atpName + "_BND" + bundleId;

		logger.info("Generated bundle name={} using ATP={} bundleId={}", bundleName, atpName, bundleId);

		return bundleName;
	}

	private void validateSimRange(String simRange) {

		logger.info("Validating SIM/IMSI range");

		if (simRange == null || simRange.isBlank()) {

			logger.error("SIM/IMSI range is null or empty");

			throw new IllegalArgumentException("SIM/IMSI range cannot be empty");
		}

		String[] values = simRange.split("-", -1);

		if (values.length != 3) {

			logger.error("Invalid SIM/IMSI range format. Expected 3 values");

			throw new IllegalArgumentException(
					"Invalid SIM/IMSI range: " + simRange + ". Expected format: I-<rangeFrom>-<rangeTo>");
		}

		if (values[0] == null || values[0].isBlank()) {

			logger.error("SIM/IMSI range include/exclude flag is missing");

			throw new IllegalArgumentException("Include/exclude flag is mandatory");
		}

		if (values[1] == null || values[1].isBlank()) {

			logger.error("SIM/IMSI range from value is missing");

			throw new IllegalArgumentException("SIM/IMSI range from is mandatory");
		}

		if (values[2] == null || values[2].isBlank()) {

			logger.error("SIM/IMSI range to value is missing");

			throw new IllegalArgumentException("SIM/IMSI range to is mandatory");
		}

		logger.info("SIM/IMSI range validation completed successfully");
	}

	private void validateRequest(AtpRequest request) {

		logger.info("Starting Bundle request validation");

		if (request == null) {

			logger.error("Bundle request is null");

			throw new IllegalArgumentException("Bundle request cannot be null");
		}

		logger.info("Validating Bundle request for ATP={} networkId={}", request.getAtpName(), request.getNetworkId());

		if (request.getNetworkId() == null) {

			logger.error("Validation failed: networkId is missing");

			throw new IllegalArgumentException("networkId is mandatory");
		}

		if (isBlank(request.getCreatedBy())) {

			logger.error("Validation failed: createdBy is missing");

			throw new IllegalArgumentException("createdBy is mandatory");
		}

		if (isBlank(request.getValidFrom())) {

			logger.error("Validation failed: validFrom is missing");

			throw new IllegalArgumentException("validFrom is mandatory");
		}

		if (isBlank(request.getValidTo())) {

			logger.error("Validation failed: validTo is missing");

			throw new IllegalArgumentException("validTo is mandatory");
		}

		logger.info("Validating Bundle date fields validFrom={} validTo={}", request.getValidFrom(),
				request.getValidTo());

		/*
		 * The procedure passes these directly to TO_DATE using MM/DD/YYYY.
		 */
		validateDateFormat(request.getValidFrom(), "validFrom");

		validateDateFormat(request.getValidTo(), "validTo");

		if (isBlank(request.getSimImsiFlag())) {

			logger.error("Validation failed: simImsiFlag is missing");

			throw new IllegalArgumentException("simImsiFlag is mandatory");
		}

		logger.info("Bundle request validation completed successfully for ATP={}", request.getAtpName());
	}

	private void validateBucketIds(List<String> bucketIds) {

		logger.info("Validating bucket IDs for Bundle creation");

		if (bucketIds == null || bucketIds.isEmpty()) {

			logger.error("Validation failed: no bucket IDs supplied");

			throw new IllegalArgumentException("At least one bucket is required for bundle creation");
		}

		logger.info("Bucket ID validation completed successfully. bucketCount={}", bucketIds.size());
	}

	private void validateDateFormat(String value, String fieldName) {

		logger.info("Validating date format fieldName={} value={}", fieldName, value);

		if (!value.matches("^(0[1-9]|1[0-2])/" + "(0[1-9]|[12][0-9]|3[01])/" + "\\d{4}$")) {

			logger.error("Invalid date format fieldName={} value={}", fieldName, value);

			throw new IllegalArgumentException(fieldName + " must be in MM/DD/YYYY format");
		}

		logger.info("Date format validation successful fieldName={}", fieldName);
	}

	private boolean isBlank(String value) {

		return value == null || value.trim().isEmpty();
	}
}