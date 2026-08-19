package com.xius.Lb.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xius.Lb.Dto.AtpRequest;
import com.xius.Lb.Dto.BalanceCategoryRequest;
import com.xius.Lb.Dto.ZoneGroupRequest;
import com.xius.Lb.repo.ServicePlanRepository;

@Service
public class PlanService {

	private static final Logger logger = LoggerFactory.getLogger(PlanService.class);

	private final ServicePlanRepository servicePlanRepository;

	public PlanService(ServicePlanRepository servicePlanRepository) {
		this.servicePlanRepository = servicePlanRepository;
	}

	@Transactional
	public List<Long> createServicePlans(AtpRequest request, Long bundleId) {

		logger.info("Starting Service Plan creation for ATP={} networkId={} bundleId={}",
				request != null ? request.getAtpName() : null, request != null ? request.getNetworkId() : null,
				bundleId);
		
		
		if (Integer.valueOf(2).equals(request.getTypeOfService())
		        && request.getBillingServiceType() != null) {

		    logger.info(
		            "Creating Service Plan using billingServiceType={} for ATP={}",
		            request.getBillingServiceType(),
		            request.getAtpName());

		    Long servicePlanId = createServicePlanForBillingServiceType(
		            request,
		            request.getBillingServiceType());

		    List<Long> servicePlanIds = new ArrayList<>();
		    servicePlanIds.add(servicePlanId);

		    logger.info(
		            "Billing Service Plan created successfully servicePlanId={} billingServiceType={}",
		            servicePlanId,
		            request.getBillingServiceType());

		    return servicePlanIds;
		}
//		if (bundleId == null) {
//
//			logger.error("Service Plan creation failed: bundleId is null");
//
//			throw new IllegalArgumentException("bundleId is mandatory for service plan creation");
//		}

//		logger.info("Service Plan request validation completed successfully for ATP={}", request.getAtpName());
		
		validateRequest(request);

		Set<String> serviceTypes = new LinkedHashSet<>();

		for (BalanceCategoryRequest balanceCategory : request.getBalanceCategories()) {

			String category = balanceCategory.getBalanceCategory().trim().toUpperCase();

			logger.info("Processing balance category={} for ATP={}", category, request.getAtpName());

			serviceTypes.add(category);
		}

		logger.info("Resolved service types={} for ATP={}", serviceTypes, request.getAtpName());

		List<Long> servicePlanIds = new ArrayList<>();

		for (String balanceCategory : serviceTypes) {

			logger.info("Creating Service Plan for balanceCategory={} ATP={}", balanceCategory, request.getAtpName());

			Long servicePlanId = createServicePlan(request, balanceCategory);

			servicePlanIds.add(servicePlanId);

			logger.info("Service Plan created successfully servicePlanId={} balanceCategory={}", servicePlanId,
					balanceCategory);
		}

		logger.info("Service Plan creation completed successfully for ATP={}. servicePlanIds={}", request.getAtpName(),
				servicePlanIds);

		return servicePlanIds;
	}

	/**
	 * Public entry point used by Modify ATP to create exactly one new
	 * service plan for a newly-required balance category type, reusing the
	 * same logic as normal ATP creation ({@link #createServicePlans}).
	 */
	@Transactional
	public Long createSingleServicePlan(AtpRequest request, String balanceCategory) {

		logger.info("Creating single Service Plan for Modify ATP balanceCategory={} ATP={}", balanceCategory,
				request != null ? request.getAtpName() : null);

		return createServicePlan(request, balanceCategory);
	}

	/**
	 * Updates an existing service plan in place for Modify ATP. Only field
	 * values are re-written - the service plan row itself is never
	 * dropped/recreated. DATA zone mappings (no natural key) are resynced
	 * by delete-then-reinsert.
	 */
	@Transactional
	public void updateServicePlan(AtpRequest request, String balanceCategory, Long servicePlanId) {

		String category = balanceCategory.trim().toUpperCase();

		logger.info("Updating servicePlanId={} balanceCategory={} for ATP={}", servicePlanId, category,
				request.getAtpName());

		String limitedHoursYn = determineLimitedHours(request.getApplicableFromHrs(), request.getApplicableToHrs());

		Long zoneGroupId = null;
		List<Long> dataZoneGroupIds = Collections.emptyList();

		if ("VOICE".equalsIgnoreCase(category) || "SMS".equalsIgnoreCase(category)) {

			zoneGroupId = getZoneGroupIdWithRatingY(request.getZoneGroup());

		} else if ("DATA".equalsIgnoreCase(category)) {

			dataZoneGroupIds = getZoneGroupIdsWithRatingY(request.getDataZoneGroupId());
		}

		Long mtCalendarId = request.getCalendarConfig() != null ? Long.valueOf(request.getCalendarConfig()) : null;

		try {

			servicePlanRepository.updateServicePlan(servicePlanId, limitedHoursYn, request.getApplicableFromHrs(),
					request.getApplicableToHrs(), request.getAllowMtc(), request.getAllowMoc(),
					request.getAllowNldMo(), request.getAllowIldMo(), request.getRatingType(), zoneGroupId,
					request.getAllowNationalRoamingData(), request.getAllowInternationalRoamingData(), mtCalendarId,
					resolveZoneBasedVipFlag(request));

			logger.info("Successfully updated servicePlanId={}", servicePlanId);

		} catch (Exception ex) {

			logger.error("Error updating servicePlanId={} balanceCategory={}", servicePlanId, category, ex);

			throw ex;
		}

		if ("DATA".equalsIgnoreCase(category)) {

			servicePlanRepository.deleteDataZoneMappings(servicePlanId);

			if (!dataZoneGroupIds.isEmpty()) {

				servicePlanRepository.insertDataZoneMappings(servicePlanId, dataZoneGroupIds);
			}
		}

		logger.info("Completed Service Plan update servicePlanId={}", servicePlanId);
	}

	private Long createServicePlan(AtpRequest request, String balanceCategory) {

		logger.info("Starting Service Plan creation balanceCategory={} ATP={} networkId={}", balanceCategory,
				request.getAtpName(), request.getNetworkId());

		Integer typeOfService = resolveTypeOfService(balanceCategory);

		logger.info("Resolved typeOfService={} for balanceCategory={}", typeOfService, balanceCategory);

		Long servicePlanId = servicePlanRepository.getNextServicePlanId();

		logger.info("Generated servicePlanId={} for balanceCategory={}", servicePlanId, balanceCategory);

		String servicePlanDesc = generateServicePlanDescription(request.getAtpName(), balanceCategory, servicePlanId);

		logger.info("Generated servicePlanDesc={} for servicePlanId={}", servicePlanDesc, servicePlanId);

		boolean exists = servicePlanRepository.existsServicePlan(request.getNetworkId(), servicePlanDesc);

		if (exists) {

			logger.error("Service Plan already exists servicePlanDesc={} networkId={}", servicePlanDesc,
					request.getNetworkId());

			throw new IllegalArgumentException("Service plan already exists: " + servicePlanDesc);
		}

		logger.info("No existing Service Plan found for servicePlanDesc={}", servicePlanDesc);

		String servicePlanType = servicePlanRepository.getRatingApplicableYn(typeOfService);

		logger.info("Resolved servicePlanType={} for typeOfService={} servicePlanId={}", servicePlanType, typeOfService,
				servicePlanId);

		String limitedHoursYn = determineLimitedHours(request.getApplicableFromHrs(), request.getApplicableToHrs());

		logger.info("Resolved limitedHoursYn={} for servicePlanId={} fromHours={} toHours={}", limitedHoursYn,
				servicePlanId, request.getApplicableFromHrs(), request.getApplicableToHrs());

		Long zoneGroupId = null;
		Long smsZoneGroupId = null;

		List<Long> dataZoneGroupIds = Collections.emptyList();

		if ("VOICE".equalsIgnoreCase(balanceCategory)) {

			zoneGroupId = getZoneGroupIdWithRatingY(request.getZoneGroup());

			logger.info("Resolved VOICE zoneGroupId={} for servicePlanId={}", zoneGroupId, servicePlanId);

		} else if ("SMS".equalsIgnoreCase(balanceCategory)) {

			zoneGroupId = getZoneGroupIdWithRatingY(request.getZoneGroup());

			logger.info("Resolved SMS zoneGroupId={} for servicePlanId={}", zoneGroupId, servicePlanId);

		} else if ("DATA".equalsIgnoreCase(balanceCategory)) {

			dataZoneGroupIds = getZoneGroupIdsWithRatingY(request.getDataZoneGroupId());

			logger.info("Resolved DATA zoneGroupIds={} for servicePlanId={}", dataZoneGroupIds, servicePlanId);
		}

		Long nsLocalOnnetCalendarId = null;
		Long nsLocalOffnetCalendarId = null;
		Long nsNldCalendarId = null;
		Long nsIldCalendarId = null;
		Long smsCalendarId = null;
		Long mtCalendarId = Long.valueOf(request.getCalendarConfig());
		Long mmsCalendarId = null;
		 String allowMtc=request.getAllowMtc();
		 String allowMoc =request.getAllowMoc();
		 String allowNldMo =request.getAllowNldMo();
		  String allowIldMo=request.getAllowIldMo();

		Long deviceGroupId = null;
		String status="AP";

		List<String> periodicChargeIds = new ArrayList<>();

		List<String> roamingMappings = buildRoamingMappings(request);

		logger.info("Prepared Service Plan mappings servicePlanId={} periodicChargeCount={} roamingMappingCount={}",
				servicePlanId, periodicChargeIds.size(), roamingMappings.size());

		try {

			logger.info("Inserting Service Plan servicePlanId={} description={} typeOfService={}", servicePlanId,
					servicePlanDesc, typeOfService);

			servicePlanRepository.insertServicePlan(request.getNetworkId(), servicePlanId, servicePlanDesc,
					servicePlanType, typeOfService, null, limitedHoursYn, request.getApplicableFromHrs(),
					request.getApplicableToHrs(), allowMtc, allowMoc, allowNldMo, allowIldMo, null, request.getRatingType(), zoneGroupId,
					smsZoneGroupId, nsLocalOnnetCalendarId, nsLocalOffnetCalendarId, nsNldCalendarId, nsIldCalendarId,
					null, null, request.getCreatedBy(),status, smsCalendarId, null, request.getAllowNationalRoamingData(),
					request.getAllowInternationalRoamingData(), mtCalendarId, deviceGroupId, mmsCalendarId, null, null,
					null, null, resolveZoneBasedVipFlag(request));

			logger.info("Successfully inserted Service Plan servicePlanId={} networkId={}", servicePlanId,
					request.getNetworkId());

		} catch (Exception ex) {

			logger.error("Error inserting Service Plan servicePlanId={} balanceCategory={} networkId={}", servicePlanId,
					balanceCategory, request.getNetworkId(), ex);

			throw ex;
		}

		if ("DATA".equalsIgnoreCase(balanceCategory) && !dataZoneGroupIds.isEmpty()) {

			logger.info("Inserting DATA zone mappings servicePlanId={} zoneGroupIds={}", servicePlanId,
					dataZoneGroupIds);

			try {

				servicePlanRepository.insertDataZoneMappings(servicePlanId, dataZoneGroupIds);

				logger.info("Successfully inserted DATA zone mappings servicePlanId={} count={}", servicePlanId,
						dataZoneGroupIds.size());

			} catch (Exception ex) {

				logger.error("Error inserting DATA zone mappings servicePlanId={} zoneGroupIds={}", servicePlanId,
						dataZoneGroupIds, ex);

				throw ex;
			}
		}

		logger.info("Inserting periodic charge mappings servicePlanId={} count={}", servicePlanId,
				periodicChargeIds.size());

		try {

			servicePlanRepository.insertChargeMappings(servicePlanId, request.getNetworkId(), periodicChargeIds);

			logger.info("Successfully inserted periodic charge mappings servicePlanId={}", servicePlanId);

		} catch (Exception ex) {

			logger.error("Error inserting periodic charge mappings servicePlanId={} networkId={}", servicePlanId,
					request.getNetworkId(), ex);

			throw ex;
		}

		logger.info("Inserting roaming mappings servicePlanId={} count={}", servicePlanId, roamingMappings.size());

		try {

			servicePlanRepository.insertRoamingMappings(servicePlanId, request.getNetworkId(), roamingMappings);

			logger.info("Successfully inserted roaming mappings servicePlanId={}", servicePlanId);

		} catch (Exception ex) {

			logger.error("Error inserting roaming mappings servicePlanId={} networkId={}", servicePlanId,
					request.getNetworkId(), ex);

			throw ex;
		}

		logger.info("Service Plan creation completed servicePlanId={} balanceCategory={}", servicePlanId,
				balanceCategory);

		return servicePlanId;
	}
	
	
	
	private Long createServicePlanForBillingServiceType(
	        AtpRequest request,
	        Integer billingServiceType) {

	    logger.info(
	            "Starting Service Plan creation using billingServiceType={} ATP={} networkId={}",
	            billingServiceType,
	            request.getAtpName(),
	            request.getNetworkId());

	    Long servicePlanId =
	            servicePlanRepository.getNextServicePlanId();

	    String servicePlanDesc =
	            request.getAtpName() + "_SP" + servicePlanId;

	    logger.info(
	            "Generated servicePlanId={} servicePlanDesc={}",
	            servicePlanId,
	            servicePlanDesc);

	    boolean exists =
	            servicePlanRepository.existsServicePlan(
	                    request.getNetworkId(),
	                    servicePlanDesc);

	    if (exists) {
	        throw new IllegalArgumentException(
	                "Service plan already exists: " + servicePlanDesc);
	    }

	    // IMPORTANT:
	    // Use billingServiceType to get servicePlanType
	    String servicePlanType =
	            servicePlanRepository.getRatingApplicableYn(
	                    billingServiceType);

	    logger.info(
	            "Resolved servicePlanType={} using billingServiceType={}",
	            servicePlanType,
	            billingServiceType);

	    String status = "AP";

	    servicePlanRepository.insertSimpleServicePlan(
	            request.getNetworkId(),
	            servicePlanId,
	            servicePlanDesc,
	            servicePlanType,
	            billingServiceType,
	            status);

	    logger.info(
	            "Successfully created simple Service Plan servicePlanId={} " +
	            "billingServiceType={} servicePlanType={} status={}",
	            servicePlanId,
	            billingServiceType,
	            servicePlanType,
	            status);

	    return servicePlanId;
	}

	private Long getZoneGroupIdWithRatingY(List<ZoneGroupRequest> zoneGroups) {

		if (zoneGroups == null || zoneGroups.isEmpty()) {

			logger.info("No zone groups supplied while resolving ratingFlag=Y zoneGroupId");

			return null;
		}

		Long zoneGroupId = zoneGroups.stream().filter(z -> z != null)
				.filter(z -> "Y".equalsIgnoreCase(z.getRatingFlag())).map(ZoneGroupRequest::getId)
				.filter(id -> id != null).findFirst().orElse(null);

		logger.info("Resolved ratingFlag=Y zoneGroupId={} from {} zone group(s)", zoneGroupId, zoneGroups.size());

		return zoneGroupId;
	}

	private List<Long> getZoneGroupIdsWithRatingY(List<ZoneGroupRequest> dataZoneGroupId) {

		if (dataZoneGroupId == null || dataZoneGroupId.isEmpty()) {

			logger.info("No DATA zone groups supplied while resolving ratingFlag=Y mappings");

			return Collections.emptyList();
		}

		List<Long> zoneGroupIds = dataZoneGroupId.stream().filter(Objects::nonNull)
				.filter(z -> "Y".equalsIgnoreCase(z.getRatingFlag())).map(ZoneGroupRequest::getId)
				.filter(Objects::nonNull).collect(Collectors.toList());

		logger.info("Resolved DATA ratingFlag=Y zoneGroupIds={} from {} zone group(s)", zoneGroupIds,
				dataZoneGroupId.size());

		return zoneGroupIds;
	}

	private Integer resolveTypeOfService(String balanceCategory) {

		logger.info("Resolving typeOfService for balanceCategory={}", balanceCategory);

		Integer typeOfService = switch (balanceCategory) {

		case "VOICE" -> 1;

		case "SMS" -> 2;

		case "DATA" -> 3;

		default -> {

			logger.error("Unsupported balance category for Service Plan: {}", balanceCategory);

			throw new IllegalArgumentException("Unsupported balance category for service plan: " + balanceCategory);
		}
		};

		logger.info("Resolved typeOfService={} for balanceCategory={}", typeOfService, balanceCategory);

		return typeOfService;
	}

	/**
	 * Service plan description generated from ATP name.
	 */
	private String generateServicePlanDescription(String atpName, String balanceCategory, Long servicePlanId) {

		String description = atpName + "_" + balanceCategory + "_SP" + servicePlanId;

		logger.info("Generated Service Plan description={} for ATP={} balanceCategory={} servicePlanId={}", description,
				atpName, balanceCategory, servicePlanId);

		return description;
	}

	/**
	 * Same logic as procedure.
	 */
	private String determineLimitedHours(Integer fromHours, Integer toHours) {

		if (fromHours == null && toHours == null) {

			logger.info("No applicable hour range configured. limitedHoursYn=N");

			return "N";
		}

		logger.info("Applicable hour range configured fromHours={} toHours={}. limitedHoursYn=Y", fromHours, toHours);

		return "Y";
	}

	private String resolveZoneBasedVipFlag(AtpRequest request) {

		if (request.getVipPlanFlagYn() != null && !request.getVipPlanFlagYn().isBlank()) {

			logger.info("Using requested zoneBasedVipPlanFlagYn={} for ATP={}", request.getVipPlanFlagYn(),
					request.getAtpName());

			return request.getVipPlanFlagYn();
		}

		logger.info("vipPlanFlagYn not provided for ATP={}. Defaulting to N", request.getAtpName());

		return "N";
	}

	private List<String> buildRoamingMappings(AtpRequest request) {

		List<String> roamingNetworks = request.getRoamingNetworks();

		if (roamingNetworks == null || roamingNetworks.isEmpty()) {

			logger.info("No roaming networks supplied for ATP={}", request.getAtpName());

			return new ArrayList<>();
		}

		if (roamingNetworks.size() == 1 && "ALL".equalsIgnoreCase(roamingNetworks.get(0))) {

			logger.info("Roaming network configured as ALL for ATP={}. No specific roaming mappings created",
					request.getAtpName());

			return new ArrayList<>();
		}

		logger.info("Specific roaming networks supplied for ATP={}. Count={}", request.getAtpName(),
				roamingNetworks.size());

		/*
		 * Existing business logic intentionally returns an empty list.
		 */
		return new ArrayList<>();
	}

	private void validateRequest(AtpRequest request) {

		logger.info("Starting Service Plan request validation");

		if (request == null) {

			logger.error("Service Plan request is null");

			throw new IllegalArgumentException("Request cannot be null");
		}

		logger.info("Validating Service Plan request ATP={} networkId={}", request.getAtpName(),
				request.getNetworkId());

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

		if (isBlank(request.getRatingType())) {

			logger.error("Validation failed: ratingType is missing for ATP={}", request.getAtpName());

			throw new IllegalArgumentException("ratingType is mandatory for service plan creation");
		}

		if (request.getBalanceCategories() == null || request.getBalanceCategories().isEmpty()) {

			logger.error("Validation failed: no balance categories supplied for ATP={}", request.getAtpName());

			throw new IllegalArgumentException("At least one balance category is required");
		}

		validateYn(request.getVipPlanFlagYn(), "vipPlanFlagYn");

		validateYn(request.getAllowNationalRoamingData(), "allowNationalRoamingData");

		validateYn(request.getAllowInternationalRoamingData(), "allowInternationalRoamingData");

		logger.info("Service Plan request validation completed successfully for ATP={}", request.getAtpName());
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
}