package com.xius.Lb.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xius.Lb.Dto.AtpDetailsResponse;
import com.xius.Lb.Dto.AtpListItem;
import com.xius.Lb.Dto.AtpModifyResponse;
import com.xius.Lb.Dto.AtpRequest;
import com.xius.Lb.Dto.AtpResponse;
import com.xius.Lb.Dto.BalanceCategoryRequest;
import com.xius.Lb.repo.AtpDetailsRepository;
import com.xius.Lb.repo.AtpDetailsRepository.ServicePlanRow;
import com.xius.Lb.repo.AtpRepository;
import com.xius.Lb.repo.AtpRepository.BasicServiceInfo;

@Service
public class AtpService {

	private static final Logger logger = LoggerFactory.getLogger(AtpService.class);

	// service_plan_type_id -> balance category, matching
	// PlanService#resolveTypeOfService / AtpDetailsService's
	// TYPE_OF_SERVICE_* constants.
	private static final int TYPE_OF_SERVICE_VOICE = 1;
	private static final int TYPE_OF_SERVICE_SMS = 2;
	private static final int TYPE_OF_SERVICE_DATA = 3;

	private final AtpRepository atpRepository;
	private final AtpDetailsRepository atpDetailsRepository;
	private final AtpDetailsService atpDetailsService;
	private final BucketService bucketService;
	private final BundleService1 bundleService;
	private final PlanService servicePlanService;
	private final TariffPlanService tariffPlanService;

	public AtpService(AtpRepository atpRepository, AtpDetailsRepository atpDetailsRepository,
			AtpDetailsService atpDetailsService, BucketService bucketService, BundleService1 bundleService,
			PlanService servicePlanService, TariffPlanService tariffPlanService) {

		this.atpRepository = atpRepository;
		this.atpDetailsRepository = atpDetailsRepository;
		this.atpDetailsService = atpDetailsService;
		this.bucketService = bucketService;
		this.bundleService = bundleService;
		this.servicePlanService = servicePlanService;
		this.tariffPlanService = tariffPlanService;
	}

	/**
	 * List ATPs (service packages) for a network, restricted to
	 * ATP_CATEGORY = 'OT'. Backs the left-pane list on the ATP Create page.
	 */
	public List<AtpListItem> getAllAtps(Long networkId) {

		logger.info("Fetching ATP list networkId={}", networkId);

		if (networkId == null) {

			logger.error("getAllAtps failed: networkId is missing");

			throw new IllegalArgumentException("networkId is mandatory");
		}

		List<AtpListItem> list = atpRepository.getAllAtps(networkId);

		logger.debug("getAllAtps result size={} networkId={}", list.size(), networkId);

		return list;
	}

	@Transactional
	public AtpResponse createAtp(AtpRequest request) {

		logger.info("Starting ATP creation for ATP={} networkId={}", request != null ? request.getAtpName() : null,
				request != null ? request.getNetworkId() : null);

		validateRequest(request);

		logger.info("ATP request validation completed for ATP={}", request.getAtpName());

		validateDuplicateAtp(request);

		logger.info("ATP duplicate validation completed for ATP={}", request.getAtpName());

		logger.info("Starting bucket creation for ATP={}", request.getAtpName());

		List<String> bucketIds = bucketService.createBuckets(request);

		logger.info("Bucket creation completed for ATP={} bucketIds={}", request.getAtpName(), bucketIds);

		if (bucketIds == null || bucketIds.isEmpty()) {

			logger.error("No buckets were created for ATP={}", request.getAtpName());

			throw new IllegalStateException("No buckets were created for ATP: " + request.getAtpName());
		}

		logger.info("Starting Bundle creation for ATP={} bucketCount={}", request.getAtpName(), bucketIds.size());

		Long bundleId = bundleService.createBundle(request, bucketIds);

		logger.info("Bundle creation completed for ATP={} bundleId={}", request.getAtpName(), bundleId);

		if (bundleId == null) {

			logger.error("Bundle creation failed for ATP={}", request.getAtpName());

			throw new IllegalStateException("Bundle creation failed for ATP: " + request.getAtpName());
		}

		logger.info("Starting Service Plan creation for ATP={} bundleId={}", request.getAtpName(), bundleId);

		List<Long> servicePlanIds = servicePlanService.createServicePlans(request, bundleId);

		logger.info("Service Plan creation completed for ATP={} servicePlanIds={}", request.getAtpName(),
				servicePlanIds);

//		validateCreatedServicePlans(servicePlanIds);

		logger.info("Created Service Plan validation completed for ATP={}", request.getAtpName());

		Long atpId = atpRepository.getNextAtpId();

		logger.info("Generated ATP ID={} for ATP={}", atpId, request.getAtpName());

		createAtpPackage(atpId, request);

		logger.info("ATP package created successfully atpId={} ATP={}", atpId, request.getAtpName());

		mapServicePlansToAtp(atpId, request, servicePlanIds);

		logger.info("Service Plan to ATP mapping completed atpId={} servicePlanIds={}", atpId, servicePlanIds);

		mapBundleToAtp(atpId, bundleId, request);

		logger.info("Bundle to ATP mapping completed atpId={} bundleId={}", atpId, bundleId);

		mapServicesToAtp(atpId, request, servicePlanIds);

		logger.info("Service to ATP mapping completed atpId={} ATP={}", atpId, request.getAtpName());

		logger.info("Starting Tariff Plan creation for ATP={} servicePlanIds={}", request.getAtpName(), servicePlanIds);

		Long tariffPlanId = tariffPlanService.createTariffPlan(request, servicePlanIds);

		logger.info("Tariff Plan creation completed tariffPlanId={} ATP={}", tariffPlanId, request.getAtpName());

		logger.info("ATP creation completed successfully atpId={} bundleId={} tariffPlanId={} ATP={}", atpId, bundleId,
				tariffPlanId, request.getAtpName());

		return new AtpResponse(atpId, bundleId, bucketIds, servicePlanIds, tariffPlanId, "ATP created successfully");
	}

	// =================================================================
	// MODIFY ATP
	// =================================================================

	/**
	 * Modify ATP: the frontend re-submits the same shape returned by
	 * GET /atp/{atpId} (form pre-filled from existing data), and this diffs
	 * it against what's persisted:
	 *
	 * - a balance category whose bucketId matches an existing bucket ->
	 *   field-level update on that bucket (and its service plan)
	 * - a balance category with no bucketId, or a bucketId not found on the
	 *   existing ATP -> treated as newly added: created via the exact same
	 *   creation logic as ATP create, then mapped in
	 * - an existing bucketId missing from the payload -> the user removed
	 *   that balance category: only the bundle<->bucket mapping is deleted
	 *   (bucket row, and everything under it, is left untouched). If that
	 *   was the last bucket needing a given service-plan type, the
	 *   ATP<->service-plan mapping (and Tariff-Plan<->service-plan mapping,
	 *   if tariffPlanId was supplied) is unmapped the same way.
	 *
	 * ATP core fields, Bundle dates/VIP flag, SIM/IMSI ranges and
	 * derivedServiceSelections are synced the same way - field updates or
	 * mapping-only add/remove, never a drop/recreate of the hierarchy.
	 */
	@Transactional
	public AtpModifyResponse modifyAtp(Long atpId, AtpRequest request) {

		logger.info("Starting Modify ATP atpId={} ATP={}", atpId, request != null ? request.getAtpName() : null);

		if (atpId == null) {

			logger.error("Modify ATP failed: atpId is missing");

			throw new IllegalArgumentException("atpId is mandatory");
		}

		validateRequest(request);

		logger.info("Modify ATP request validation completed atpId={}", atpId);

		AtpDetailsResponse existing = atpDetailsService.getAtpDetails(atpId);

		Long bundleId = atpDetailsService.findBundleId(atpId);

		if (bundleId == null) {

			logger.error("Modify ATP failed: no bundle mapped for atpId={}", atpId);

			throw new IllegalStateException("No bundle found for ATP: " + atpId);
		}

		// ---- buckets / balance categories ----------------------------------

		Map<String, BalanceCategoryRequest> existingByBucketId = new LinkedHashMap<>();

		if (existing.getBalanceCategories() != null) {

			for (BalanceCategoryRequest bc : existing.getBalanceCategories()) {

				if (bc != null && !isBlank(bc.getBucketId())) {
					existingByBucketId.put(bc.getBucketId(), bc);
				}
			}
		}

		List<String> addedBucketIds = new ArrayList<>();
		List<String> updatedBucketIds = new ArrayList<>();
		List<String> removedBucketIds = new ArrayList<>();

		Set<String> keptBucketIds = new HashSet<>();
		Set<String> finalBalanceCategoryTypes = new LinkedHashSet<>();

		List<BalanceCategoryRequest> incomingBalances = request.getBalanceCategories() == null ? new ArrayList<>()
				: request.getBalanceCategories();

		for (BalanceCategoryRequest bc : incomingBalances) {

			if (bc == null) {
				continue;
			}

			String bucketId = bc.getBucketId();

			if (!isBlank(bucketId) && existingByBucketId.containsKey(bucketId)) {

				keptBucketIds.add(bucketId);

				bucketService.updateBucket(request, bc, existingByBucketId.get(bucketId));

				updatedBucketIds.add(bucketId);

			} else {

				String newBucketId = bucketService.createSingleBucket(request, bc);

				bundleService.mapBucketToBundle(bundleId, request.getNetworkId(), newBucketId);

				addedBucketIds.add(newBucketId);
			}

			finalBalanceCategoryTypes.add(bc.getBalanceCategory().trim().toUpperCase());
		}

		for (String existingBucketId : existingByBucketId.keySet()) {

			if (!keptBucketIds.contains(existingBucketId)) {

				bundleService.unmapBucketFromBundle(bundleId, existingBucketId);

				removedBucketIds.add(existingBucketId);
			}
		}

		logger.info("Modify ATP bucket sync completed atpId={} added={} updated={} removed={}", atpId,
				addedBucketIds, updatedBucketIds, removedBucketIds);

		// ---- service plans (one per VOICE/SMS/DATA balance category, same as
		// PlanService#createServicePlans) --------------------------------------

		Set<String> finalServicePlanCategories = new LinkedHashSet<>();

		for (String category : finalBalanceCategoryTypes) {

			if ("VOICE".equals(category) || "SMS".equals(category) || "DATA".equals(category)) {
				finalServicePlanCategories.add(category);
			}
		}

		// List<Long> existingServicePlanIds = atpDetailsRepository.findServicePlanIdsForAtp(atpId);

		List<Long> existingServicePlanIds =atpDetailsRepository.findServicePlanIdsForAtp(atpId);

if (existingServicePlanIds == null || existingServicePlanIds.isEmpty()) {
    throw new IllegalStateException(
            "No Service Plans found for ATP: " + atpId);
}

Long tariffPlanId =
        tariffPlanService.findTariffPlanIdByServicePlanIds(
                existingServicePlanIds);

		List<ServicePlanRow> existingServicePlanRows = atpDetailsRepository.findServicePlans(existingServicePlanIds);

		Map<String, Long> existingServicePlanByCategory = new LinkedHashMap<>();

		for (ServicePlanRow row : existingServicePlanRows) {

			String category = mapTypeOfServiceToCategory(row.typeOfService());

			if (category != null) {
				existingServicePlanByCategory.put(category, row.servicePlanId());
			}
		}

		List<Long> addedServicePlanIds = new ArrayList<>();
		List<Long> updatedServicePlanIds = new ArrayList<>();
		List<Long> removedServicePlanIds = new ArrayList<>();

		// Long tariffPlanId = request.getTariffPlanId();

		for (String category : finalServicePlanCategories) {

			Long existingServicePlanId = existingServicePlanByCategory.get(category);

			if (existingServicePlanId != null) {

				servicePlanService.updateServicePlan(request, category, existingServicePlanId);

				updatedServicePlanIds.add(existingServicePlanId);

			} else {

				Long newServicePlanId = servicePlanService.createSingleServicePlan(request, category);

				atpRepository.insertServicePlanMapping(atpId, newServicePlanId, request.getNetworkId());

				if (tariffPlanId != null) {
					tariffPlanService.addServicePlanMapping(tariffPlanId, newServicePlanId, request.getNetworkId());
				}

				addedServicePlanIds.add(newServicePlanId);
			}
		}

		for (Map.Entry<String, Long> entry : existingServicePlanByCategory.entrySet()) {

			if (!finalServicePlanCategories.contains(entry.getKey())) {

				Long servicePlanId = entry.getValue();

				atpRepository.deleteServicePlanMapping(atpId, servicePlanId);

				if (tariffPlanId != null) {
					tariffPlanService.removeServicePlanMapping(tariffPlanId, servicePlanId);
				}

				removedServicePlanIds.add(servicePlanId);
			}
		}

		logger.info("Modify ATP service plan sync completed atpId={} added={} updated={} removed={}", atpId,
				addedServicePlanIds, updatedServicePlanIds, removedServicePlanIds);

		// ---- ATP core fields -------------------------------------------------
		// atpName / publicityId are locked in the UI once an ATP exists (see
		// Atpcreate.js#applyEditModeFieldLocks) — enforced here too, straight
		// off the existing row, so a direct API call can't change them either.

		atpRepository.updateAtp(atpId, existing.getAtpName(), request.getValidTo(), request.getCategoryOfferCode(),
				request.getDescription(), existing.getPublicityId());

		// ---- Bundle core fields + SIM/IMSI ranges (replace-all) --------------

		bundleService.updateBundleCore(request, bundleId);

		bundleService.syncSimImsiRanges(bundleId, request);

		// ---- derived service selections ---------------------------------------

		List<String> addedServices = new ArrayList<>();
		List<String> removedServices = new ArrayList<>();

		Set<String> existingServices = existing.getDerivedServiceSelections() == null ? new LinkedHashSet<>()
				: new LinkedHashSet<>(existing.getDerivedServiceSelections());

		Set<String> incomingServices = request.getDerivedServiceSelections() == null ? new LinkedHashSet<>()
				: new LinkedHashSet<>(request.getDerivedServiceSelections());

		for (String service : incomingServices) {

			if (!existingServices.contains(service)) {

				String[] values = parseServiceSelection(service);

				Long basicServiceId = Long.valueOf(values[0]);
				Long derivedServiceId = Long.valueOf(values[1]);

				atpRepository.insertServiceAtpMapping(request.getNetworkId(), atpId, basicServiceId,
						derivedServiceId);

				if (tariffPlanId != null) {
					tariffPlanService.addServiceMapping(tariffPlanId, request.getNetworkId(), basicServiceId,
							derivedServiceId);
				}

				addedServices.add(service);
			}
		}

		for (String service : existingServices) {

			if (!incomingServices.contains(service)) {

				String[] values = parseServiceSelection(service);

				Long basicServiceId = Long.valueOf(values[0]);
				Long derivedServiceId = Long.valueOf(values[1]);

				atpRepository.deleteServiceAtpMapping(atpId, basicServiceId, derivedServiceId);

				if (tariffPlanId != null) {
					tariffPlanService.removeServiceMapping(tariffPlanId, basicServiceId, derivedServiceId);
				}

				removedServices.add(service);
			}
		}

		logger.info("Modify ATP completed successfully atpId={} bundleId={} tariffPlanId={}", atpId, bundleId,
				tariffPlanId);

		return new AtpModifyResponse(atpId, bundleId, tariffPlanId, addedBucketIds, updatedBucketIds,
				removedBucketIds, addedServicePlanIds, updatedServicePlanIds, removedServicePlanIds, addedServices,
				removedServices, "ATP modified successfully");
	}

	private String mapTypeOfServiceToCategory(Integer typeOfService) {

		if (typeOfService == null) {
			return null;
		}

		if (typeOfService == TYPE_OF_SERVICE_VOICE) {
			return "VOICE";
		}

		if (typeOfService == TYPE_OF_SERVICE_SMS) {
			return "SMS";
		}

		if (typeOfService == TYPE_OF_SERVICE_DATA) {
			return "DATA";
		}

		return null;
	}

	private void validateBalanceCategory(BalanceCategoryRequest balanceCategory) {

		logger.info("Starting balance category validation");

		if (balanceCategory == null) {

			logger.error("Balance category is null");

			throw new IllegalArgumentException("Balance category cannot be null");
		}

		if (isBlank(balanceCategory.getBalanceCategory())) {

			logger.error("balanceCategory is missing");

			throw new IllegalArgumentException("balanceCategory is mandatory");
		}

		if (isBlank(balanceCategory.getBucketType())) {

			logger.error("bucketType is missing for balanceCategory={}", balanceCategory.getBalanceCategory());

			throw new IllegalArgumentException("bucketType is mandatory for " + balanceCategory.getBalanceCategory());
		}

		if (balanceCategory.getUsageType() == null || balanceCategory.getUsageType().isEmpty()) {

			logger.error("usageType is missing for balanceCategory={}", balanceCategory.getBalanceCategory());

			throw new IllegalArgumentException("usageType is mandatory for " + balanceCategory.getBalanceCategory());
		}

		if (isBlank(balanceCategory.getBucketUnitType())) {

			logger.error("bucketUnitType is missing for balanceCategory={}", balanceCategory.getBalanceCategory());

			throw new IllegalArgumentException(
					"bucketUnitType is mandatory for " + balanceCategory.getBalanceCategory());
		}

		if (balanceCategory.getBucketUnitValue() == null) {

			logger.error("bucketUnitValue is missing for balanceCategory={}", balanceCategory.getBalanceCategory());

			throw new IllegalArgumentException(
					"bucketUnitValue is mandatory for " + balanceCategory.getBalanceCategory());
		}

		if (isBlank(balanceCategory.getUnlimitedUsageYn())) {

			logger.error("unlimitedUsageYn is missing for balanceCategory={}", balanceCategory.getBalanceCategory());

			throw new IllegalArgumentException(
					"unlimitedUsageYn is mandatory for " + balanceCategory.getBalanceCategory());
		}

		validateYn(balanceCategory.getUnlimitedUsageYn(), "unlimitedUsageYn");

		logger.info("Balance category validation completed successfully balanceCategory={}",
				balanceCategory.getBalanceCategory());
	}

	private String generateBundleName(String atpName) {

		String bundleName = atpName + "_BUNDLE";

		logger.info("Generated bundle name={} for ATP={}", bundleName, atpName);

		return bundleName;
	}

//	private void validateCreatedServicePlans(List<Long> servicePlanIds) {
//
//		logger.info("Validating created service plans servicePlanIds={}", servicePlanIds);
//
//		if (servicePlanIds == null || servicePlanIds.isEmpty()) {
//
//			logger.error("No service plans were created");
//
//			throw new IllegalStateException("No service plans were created");
//		}
//
//		Set<Long> uniqueIds = new HashSet<>();
//
//		for (Long servicePlanId : servicePlanIds) {
//
//			if (servicePlanId == null) {
//
//				logger.error("ServicePlanService returned null service plan ID");
//
//				throw new IllegalStateException("ServicePlanService returned null " + "service plan ID");
//			}
//
//			if (!uniqueIds.add(servicePlanId)) {
//
//				logger.error("Duplicate service plan ID returned: {}", servicePlanId);
//
//				throw new IllegalStateException("Duplicate service plan ID returned: " + servicePlanId);
//			}
//
//			logger.info("Validated servicePlanId={}", servicePlanId);
//		}
//
//		logger.info("Created service plans validated successfully count={}", servicePlanIds.size());
//	}

	private void createAtpPackage(Long atpId, AtpRequest request) {

		logger.info("Creating ATP package atpId={} ATP={} networkId={}", atpId, request.getAtpName(),
				request.getNetworkId());

		try {

			atpRepository.insertAtp(atpId,

					request.getAtpName(), null, null, request.getNetworkId(), null, null, null, null, "Y", null, null,
					null, request.getValidTo(), null, "OT", request.getPublicityId(), request.getCategoryOfferCode(), request.getDescription(),
					null);

			logger.info("Successfully inserted ATP package atpId={} ATP={}", atpId, request.getAtpName());

		} catch (Exception ex) {

			logger.error("Error inserting ATP package atpId={} ATP={} networkId={}", atpId, request.getAtpName(),
					request.getNetworkId(), ex);

			throw ex;
		}
	}

	private void mapServicePlansToAtp(Long atpId, AtpRequest request, List<Long> servicePlanIds) {

		logger.info("Starting Service Plan to ATP mapping atpId={} servicePlanIds={}", atpId, servicePlanIds);

		if (servicePlanIds == null || servicePlanIds.isEmpty()) {

			logger.error("Service plan IDs are missing for ATP mapping atpId={}", atpId);

			throw new IllegalArgumentException("Service plan IDs are required");
		}

		for (Long servicePlanId : servicePlanIds) {

			if (servicePlanId == null) {

				logger.error("Service plan ID is null for ATP mapping atpId={}", atpId);

				throw new IllegalArgumentException("Service plan ID cannot be null");
			}

			logger.info("Mapping existing servicePlanId={} to atpId={} networkId={}", servicePlanId, atpId,
					request.getNetworkId());

			try {

				atpRepository.insertServicePlanMapping(atpId, servicePlanId, request.getNetworkId());

				logger.info("Successfully mapped servicePlanId={} to atpId={}", servicePlanId, atpId);

			} catch (Exception ex) {

				logger.error("Error mapping servicePlanId={} to atpId={} networkId={}", servicePlanId, atpId,
						request.getNetworkId(), ex);

				throw ex;
			}
		}

		logger.info("Completed Service Plan to ATP mapping atpId={} servicePlanIds={}", atpId, servicePlanIds);
	}

	private void mapBundleToAtp(Long atpId, Long bundleId, AtpRequest request) {

		logger.info("Starting Bundle to ATP mapping atpId={} bundleId={}", atpId, bundleId);

		if (bundleId == null) {

			logger.error("Bundle ID is missing for ATP mapping atpId={}", atpId);

			throw new IllegalArgumentException("Bundle ID cannot be null");
		}

		try {

			atpRepository.insertAtpBundleMapping(atpId, bundleId, request.getNetworkId());

			logger.info("Successfully mapped bundleId={} to atpId={}", bundleId, atpId);

		} catch (Exception ex) {

			logger.error("Error mapping bundleId={} to atpId={} networkId={}", bundleId, atpId,
					request.getNetworkId(), ex);

			throw ex;
		}
	}

	/**
	 * Resolves the required service-plan types from:
	 *
	 * derivedServiceSelections
	 *
	 * Example:
	 *
	 * 1~1 1~2 3~1 3~5
	 */
	private Set<Integer> getRequiredServicePlanTypes(List<String> services) {

		logger.info("Resolving required service plan types from services={}", services);

		Set<Integer> requiredTypes = new HashSet<>();

		if (services == null || services.isEmpty()) {

			logger.info("No services supplied. Returning empty required service plan types");

			return requiredTypes;
		}

		for (String service : services) {

			String[] values = parseServiceSelection(service);

			Integer serviceType = Integer.valueOf(values[0]);

			requiredTypes.add(serviceType);

			logger.info("Resolved serviceType={} from service selection={}", serviceType, service);
		}

		logger.info("Required service plan types resolved={}", requiredTypes);

		return requiredTypes;
	}

	// =================================================================
	// BASIC / DERIVED SERVICE -> ATP MAPPING
	// =================================================================

	private void mapServicesToAtp(Long atpId, AtpRequest request, List<Long> servicePlanIds) {

		logger.info("Starting Basic/Derived Service to ATP mapping atpId={}", atpId);

		List<String> services = request.getDerivedServiceSelections();

		if (services == null || services.isEmpty()) {

			logger.error("derivedServiceSelections are missing for atpId={}", atpId);

			throw new IllegalArgumentException("derivedServiceSelections are mandatory");
		}

		logger.info("Processing {} service selection(s) for atpId={}", services.size(), atpId);

		for (String service : services) {

			logger.info("Processing service selection for atpId={}", atpId);

			String[] values = parseServiceSelection(service);

			Long basicServiceId = Long.valueOf(values[0]);

			Long derivedServiceId = Long.valueOf(values[1]);

			logger.info("Parsed service selection basicServiceId={} derivedServiceId={}", basicServiceId,
					derivedServiceId);

			BasicServiceInfo basicService;

			try {

				basicService = atpRepository.getBasicServiceInfo(basicServiceId);

				logger.info("Retrieved basic service basicServiceId={} serviceName={} ratingServiceYn={}",
						basicServiceId, basicService.serviceName(), basicService.ratingServiceYn());

			} catch (Exception ex) {

				logger.error("Basic service not found basicServiceId={}", basicServiceId, ex);

				throw new IllegalArgumentException("Basic service not found: " + basicServiceId);
			}

			try {

				atpRepository.insertServiceAtpMapping(request.getNetworkId(), atpId, basicServiceId, derivedServiceId);

				logger.info("Successfully mapped basicServiceId={} derivedServiceId={} to atpId={}", basicServiceId,
						derivedServiceId, atpId);

			} catch (Exception ex) {

				logger.error("Error mapping basicServiceId={} derivedServiceId={} to atpId={}", basicServiceId,
						derivedServiceId, atpId, ex);

				throw ex;
			}
		}

		logger.info("Completed Basic/Derived Service to ATP mapping atpId={}", atpId);
	}

	// =================================================================
	// SERVICE SELECTION PARSING
	// =================================================================

	private String[] parseServiceSelection(String service) {

		logger.info("Parsing service selection");

		if (isBlank(service)) {

			logger.error("Service selection is empty");

			throw new IllegalArgumentException("Service selection cannot be empty");
		}

		String[] values = service.split("~", -1);

		if (values.length != 2) {

			logger.error("Invalid derivedServiceSelection format");

			throw new IllegalArgumentException("Invalid derivedServiceSelection: " + service
					+ ". Expected format basicServiceId~derivedServiceId");
		}

		try {

			Long.valueOf(values[0].trim());
			Long.valueOf(values[1].trim());

		} catch (NumberFormatException ex) {

			logger.error("Invalid service IDs in service selection", ex);

			throw new IllegalArgumentException("Invalid service IDs in: " + service);
		}

		logger.info("Service selection parsed successfully");

		return new String[] { values[0].trim(), values[1].trim() };
	}

	private String buildServiceName(BasicServiceInfo basicService, Long derivedServiceId) {

		if (basicService == null) {

			logger.warn("Basic service information is null derivedServiceId={}", derivedServiceId);

			return String.valueOf(derivedServiceId);
		}

		if ("N".equalsIgnoreCase(basicService.ratingServiceYn())) {

			logger.info("Using basic service name={} because ratingServiceYn=N", basicService.serviceName());

			return basicService.serviceName();
		}

		try {

			String derivedServiceName = atpRepository.getDerivedServiceName(derivedServiceId);

			logger.info("Resolved derived service name for derivedServiceId={}", derivedServiceId);

			return basicService.serviceName() + "~" + derivedServiceName;

		} catch (Exception ex) {

			logger.warn("Unable to resolve derived service name for derivedServiceId={}. Using ID instead",
					derivedServiceId);

			return basicService.serviceName() + "~" + derivedServiceId;
		}
	}

	private void validateRequest(AtpRequest request) {

		logger.info("Starting ATP request validation");

		if (request == null) {

			logger.error("ATP request is null");

			throw new IllegalArgumentException("ATP request cannot be null");
		}

		if (isBlank(request.getAtpName())) {

			logger.error("Validation failed: atpName is missing");

			throw new IllegalArgumentException("atpName is mandatory");
		}

		logger.info("Validating ATP={} validTo={}", request.getAtpName(), request.getValidTo());

		if (isBlank(request.getValidTo())) {

			logger.error("Validation failed: validTo is missing for ATP={}", request.getAtpName());

			throw new IllegalArgumentException("validTo is mandatory");
		}

		validateDateFormat(request.getValidTo());

		if (request.getBalanceCategories() == null || request.getBalanceCategories().isEmpty()) {

			logger.error("Validation failed: no balance categories supplied for ATP={}", request.getAtpName());

			throw new IllegalArgumentException("At least one balance category is required");
		}

		if (request.getDerivedServiceSelections() == null || request.getDerivedServiceSelections().isEmpty()) {

			logger.error("Validation failed: derivedServiceSelections are missing for ATP={}", request.getAtpName());

			throw new IllegalArgumentException("derivedServiceSelections are mandatory");
		}

		for (BalanceCategoryRequest balanceCategory : request.getBalanceCategories()) {

			logger.info("Validating balance category for ATP={}", request.getAtpName());

			validateBalanceCategory(balanceCategory);
		}

		for (String service : request.getDerivedServiceSelections()) {

			logger.info("Validating service selection for ATP={}", request.getAtpName());

			parseServiceSelection(service);
		}

		logger.info("ATP request validation completed successfully for ATP={}", request.getAtpName());
	}

	/**
	 * Checks duplicate ATP.
	 *
	 * PDP / VDP publicity validation is separate.
	 */
	private void validateDuplicateAtp(AtpRequest request) {

		logger.info("Checking duplicate ATP ATP={} networkId={}", request.getAtpName(), request.getNetworkId());

		boolean exists = atpRepository.existsAtp(request.getNetworkId(), request.getAtpName());

		if (exists) {

			logger.error("ATP already exists ATP={} networkId={}", request.getAtpName(), request.getNetworkId());

			throw new IllegalArgumentException("ATP already exists: " + request.getAtpName());
		}

		logger.info("No duplicate ATP found ATP={}", request.getAtpName());

		if (isPdpOrVdp("OT")) {

			logger.info("Checking duplicate publicity ID publicityId={}", request.getPublicityId());

			boolean publicityExists = atpRepository.existsPublicityId(request.getPublicityId());

			if (publicityExists) {

				logger.error("Publicity ID already exists publicityId={}", request.getPublicityId());

				throw new IllegalArgumentException("Publicity ID already exists: " + request.getPublicityId());
			}

			logger.info("No duplicate publicity ID found publicityId={}", request.getPublicityId());
		}
	}

	// =================================================================
	// VALIDATION HELPERS
	// =================================================================

	private void validateDateFormat(String date) {

		logger.info("Validating date format date={}", date);

		if (!date.matches("^(0[1-9]|1[0-2])/" + "(0[1-9]|[12][0-9]|3[01])/" + "\\d{4}$")) {

			logger.error("Invalid date format date={}", date);

			throw new IllegalArgumentException("validTo must be in MM/DD/YYYY format");
		}

		logger.info("Date format validation successful");
	}

	private void validateYn(String value, String fieldName) {

		logger.info("Validating Y/N field={} value={}", fieldName, value);

		if (!"Y".equalsIgnoreCase(value) && !"N".equalsIgnoreCase(value)) {

			logger.error("Invalid Y/N value field={} value={}", fieldName, value);

			throw new IllegalArgumentException(fieldName + " must be Y or N");
		}

		logger.info("Y/N validation successful field={}", fieldName);
	}

	private boolean isPdpOrVdp(String atpCategory) {

		return "OT".equalsIgnoreCase(atpCategory) || "CA".equalsIgnoreCase(atpCategory);
	}

	private boolean isBlank(String value) {

		return value == null || value.trim().isEmpty();
	}
}