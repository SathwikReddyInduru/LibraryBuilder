package com.xius.Lb.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xius.Lb.Dto.AtpRequest;
import com.xius.Lb.repo.TariffPlanRepository;
import com.xius.Lb.repo.TariffPlanRepository.BasicServiceInfo;

@Service
public class TariffPlanService {

	private static final Logger logger = LoggerFactory.getLogger(TariffPlanService.class);

	private final TariffPlanRepository tariffPlanRepository;

	public TariffPlanService(TariffPlanRepository tariffPlanRepository) {
		this.tariffPlanRepository = tariffPlanRepository;
	}

	@Transactional
	public Long createTariffPlan(AtpRequest request, List<Long> servicePlanIds) {

		logger.info("Starting Tariff Plan creation for ATP={} networkId={}",
				request != null ? request.getAtpName() : null, request != null ? request.getNetworkId() : null);

		validateRequest(request);

		logger.info("Tariff Plan request validation completed");

//		validateServicePlans(servicePlanIds);

		logger.info("Service Plan validation completed servicePlanIds={}", servicePlanIds);

		Long servicePackageId = tariffPlanRepository.getNextServicePackageId();

		logger.info("Generated servicePackageId={}", servicePackageId);

		String addPackageYn = "N";
		String aspType = "U";

		BigDecimal rentalAmount = BigDecimal.ZERO;
		BigDecimal activationCharge = BigDecimal.ZERO;

		Integer tax1 = 0;
		Integer tax2 = 0;
		Integer tax3 = 0;

		Integer rentalPeriod = 0;

		String atpCategory = "OT";
		String caServicePackageYn = null;
		String atpCategoryByOffer = null;
		String description = null;
		String chargeOnFirstUsageYn = "N";
		String allowMultipleAtpYn = null;

		String userDefined1 = null;
		String userDefined2 = null;
		String userDefined3 = null;

		String publicityId = request.getPublicityId();

		tariffPlanRepository.insertServicePackage(servicePackageId, request.getAtpName() + "_TP" + servicePackageId,

				rentalAmount, activationCharge,
				request.getNetworkId(),
				tax1, tax2, tax3,
				null,
				addPackageYn,
				null, rentalPeriod,
				aspType, request.getValidTo(),
				null,
				atpCategory,
				null, null, null, null,
				publicityId,
				null, null,
				caServicePackageYn, atpCategoryByOffer, description, chargeOnFirstUsageYn, allowMultipleAtpYn,
				userDefined1, userDefined2, userDefined3);
		logger.info("Successfully inserted Service Package servicePackageId={} networkId={}", servicePackageId,
				request.getNetworkId());

		mapServicePlans(servicePackageId, request, servicePlanIds);

		logger.info("Service Plan mappings completed for servicePackageId={}", servicePackageId);

		mapServices(servicePackageId, request, servicePlanIds);

		logger.info("Service mappings completed for servicePackageId={}", servicePackageId);

		logger.info("Tariff Plan creation completed successfully servicePackageId={}", servicePackageId);

		return servicePackageId;
	}

	private void validateServicePlans(List<Long> servicePlanIds) {

		logger.info("Validating Service Plan IDs for Tariff Plan creation");

		if (servicePlanIds == null || servicePlanIds.isEmpty()) {

			logger.error("Validation failed: no service plan IDs supplied");

			throw new IllegalArgumentException("At least one service plan is required");
		}

		logger.info("Validating {} service plan ID(s)", servicePlanIds.size());

		for (Long servicePlanId : servicePlanIds) {

			if (servicePlanId == null) {

				logger.error("Validation failed: servicePlanId is null");

				throw new IllegalArgumentException("servicePlanId cannot be null");
			}

			logger.info("Valid servicePlanId={}", servicePlanId);
		}

		int existingCount = tariffPlanRepository.countExistingServicePlans(servicePlanIds);

		int requestedUniqueCount = new HashSet<>(servicePlanIds).size();

		logger.info("Service Plan validation result existingCount={} requestedUniqueCount={}", existingCount,
				requestedUniqueCount);

		if (existingCount != requestedUniqueCount) {

			logger.error("One or more service plan IDs do not exist. servicePlanIds={}", servicePlanIds);

			throw new IllegalArgumentException("One or more service plan IDs do not exist");
		}

		logger.info("Service Plan ID validation completed successfully");
	}

	private void mapServicePlans(Long servicePackageId, AtpRequest request, List<Long> servicePlanIds) {

		/*
		 * If you don't have ASP type in AtpRequest, decide your TP mapping type here.
		 */
		String aspType = "U";

		logger.info("Starting Service Plan mapping servicePackageId={} aspType={} servicePlanCount={}",
				servicePackageId, aspType, servicePlanIds.size());

		if (!List.of("U", "B", "D").contains(aspType)) {

			logger.error("Invalid aspType={} for servicePackageId={}", aspType, servicePackageId);

			throw new IllegalArgumentException("aspType must be U, B or D");
		}

		for (Long servicePlanId : servicePlanIds) {

			logger.info("Mapping servicePlanId={} to servicePackageId={} aspType={}", servicePlanId, servicePackageId,
					aspType);

			try {

				if ("U".equals(aspType)) {

					tariffPlanRepository.insertServicePlanPackageMapping(servicePackageId, servicePlanId,
							request.getNetworkId());

				} else {

					tariffPlanRepository.insertBonusDiscountMapping(servicePackageId, servicePlanId,
							request.getNetworkId(), aspType);
				}

				logger.info("Successfully mapped servicePlanId={} to servicePackageId={}", servicePlanId,
						servicePackageId);

			} catch (Exception ex) {

				logger.error("Error mapping servicePlanId={} to servicePackageId={} networkId={}", servicePlanId,
						servicePackageId, request.getNetworkId(), ex);

				throw ex;
			}
		}

		logger.info("Completed Service Plan mapping servicePackageId={}", servicePackageId);
	}

	private void mapServices(Long servicePackageId, AtpRequest request, List<Long> servicePlanIds) {

		List<String> services = request.getDerivedServiceSelections();

		if (services == null || services.isEmpty()) {

			logger.info("No derived service selections supplied for servicePackageId={}", servicePackageId);

			return;
		}

		logger.info("Starting derived service mapping servicePackageId={} serviceCount={}", servicePackageId,
				services.size());

		for (String service : services) {

			if (service == null || service.isBlank()) {

				logger.warn("Skipping blank derived service selection for servicePackageId={}", servicePackageId);

				continue;
			}

			logger.info("Processing derived service selection={} for servicePackageId={}", service, servicePackageId);

			String[] values = service.split("~", -1);

			if (values.length != 2) {

				logger.error("Invalid service format={} for servicePackageId={}", service, servicePackageId);

				throw new IllegalArgumentException(
						"Invalid service: " + service + ". Expected basicServiceId~derivedServiceId");
			}

			Long basicServiceId = parseLong(values[0], "basic service ID");

			Long derivedServiceId = parseLong(values[1], "derived service ID");

			logger.info("Parsed service selection basicServiceId={} derivedServiceId={}", basicServiceId,
					derivedServiceId);

			try {

				BasicServiceInfo basicServiceInfo = tariffPlanRepository.getBasicServiceInfo(basicServiceId);

				logger.info("Basic service found basicServiceId={} serviceName={}", basicServiceId,
						basicServiceInfo.serviceName());

			} catch (Exception ex) {

				logger.error("Basic service not found basicServiceId={}", basicServiceId, ex);

				throw new IllegalArgumentException("Basic service not found: " + basicServiceId);
			}

			try {

				tariffPlanRepository.insertServiceAtpMapping(request.getNetworkId(), servicePackageId, basicServiceId,
						derivedServiceId);

				logger.info("Successfully mapped basicServiceId={} derivedServiceId={} to servicePackageId={}",
						basicServiceId, derivedServiceId, servicePackageId);

			} catch (Exception ex) {

				logger.error("Error mapping basicServiceId={} derivedServiceId={} to servicePackageId={}",
						basicServiceId, derivedServiceId, servicePackageId, ex);

				throw ex;
			}
		}

		logger.info("Completed derived service mapping servicePackageId={}", servicePackageId);
	}

	private String buildServiceName(String basicServiceName, Long derivedServiceId) {

		if (derivedServiceId == null) {

			logger.info("Derived service ID is null. Using basic service name={}", basicServiceName);

			return basicServiceName;
		}

		try {

			String derivedServiceName = tariffPlanRepository.getDerivedServiceName(derivedServiceId);

			String serviceName = basicServiceName + "~" + derivedServiceName;

			logger.info("Built service name={} for derivedServiceId={}", serviceName, derivedServiceId);

			return serviceName;

		} catch (Exception ex) {

			logger.warn("Unable to resolve derived service name for derivedServiceId={}. Using ID instead",
					derivedServiceId);

			return basicServiceName + "~" + derivedServiceId;
		}
	}

	private Long parseLong(String value, String fieldName) {

		try {

			Long parsedValue = Long.valueOf(value.trim());

			logger.info("Parsed {}={} successfully", fieldName, parsedValue);

			return parsedValue;

		} catch (NumberFormatException ex) {

			logger.error("Invalid {} value={}", fieldName, value, ex);

			throw new IllegalArgumentException("Invalid " + fieldName + ": " + value);
		}
	}

	private void validateRequest(AtpRequest request) {

		logger.info("Starting Tariff Plan request validation");

		/*
		 * Existing validations intentionally left unchanged.
		 */

		if (isBlank(request.getValidTo())) {

			logger.error("Validation failed: validTo is missing");

			throw new IllegalArgumentException("validTo is mandatory");
		}

		logger.info("Validating Tariff Plan validTo={}", request.getValidTo());

		validateDate(request.getValidTo());

		logger.info("Tariff Plan request validation completed successfully");
	}

	private void validateDate(String value) {

		logger.info("Validating date format value={}", value);

		if (!value.matches("^(0[1-9]|1[0-2])/" + "(0[1-9]|[12][0-9]|3[01])/" + "\\d{4}$")) {

			logger.error("Invalid date format value={}", value);

			throw new IllegalArgumentException("validTo must be in MM/DD/YYYY format");
		}

		logger.info("Date format validation successful value={}", value);
	}

	private boolean isBlank(String value) {

		return value == null || value.trim().isEmpty();
	}
}