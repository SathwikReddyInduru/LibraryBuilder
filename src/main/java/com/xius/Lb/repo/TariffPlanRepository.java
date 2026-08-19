package com.xius.Lb.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class TariffPlanRepository {

	private final JdbcTemplate oracleJdbcTemplate;

	public TariffPlanRepository(JdbcTemplate oracleJdbcTemplate) {
		this.oracleJdbcTemplate = oracleJdbcTemplate;
	}

	public Long getNextServicePackageId() {

		String sql = """
				SELECT SEQ_SERVICE_PACK_ID.NEXTVAL
				FROM DUAL
				""";

		return oracleJdbcTemplate.queryForObject(sql, Long.class);
	}

	public List<Integer> getServicePlanTypes(List<Long> servicePlanIds) {

		if (servicePlanIds == null || servicePlanIds.isEmpty()) {

			return Collections.emptyList();
		}

		String placeholders = servicePlanIds.stream().map(id -> "?").collect(Collectors.joining(","));

		String sql = """
				SELECT TYPE_OF_SERVICE
				FROM CS_RAT_SERVICE_PLANS
				WHERE SERVICE_PLAN_ID IN (
				""" + placeholders + """
				)
				""";

		return oracleJdbcTemplate.queryForList(sql, Integer.class, servicePlanIds.toArray());
	}

	public int countExistingServicePlans(List<Long> servicePlanIds) {

		if (servicePlanIds == null || servicePlanIds.isEmpty()) {

			return 0;
		}

		String placeholders = servicePlanIds.stream().map(id -> "?").collect(Collectors.joining(","));

		String sql = """
				SELECT COUNT(DISTINCT SERVICE_PLAN_ID)
				FROM CS_RAT_SERVICE_PLANS
				WHERE SERVICE_PLAN_ID IN (
				""" + placeholders + """
				)
				""";

		Integer count = oracleJdbcTemplate.queryForObject(sql, Integer.class, servicePlanIds.toArray());

		return count == null ? 0 : count;
	}

	public void insertServicePackage(Long servicePackageId, String servicePackageDesc, BigDecimal rentalAmount,
			BigDecimal activationCharge, Long networkId, Integer tax1, Integer tax2, Integer tax3, Long chargeId,
			String addPackYn, String rentalType, Integer rentalPeriod, String aspType, String endDate,
			Integer serviceDuration,String atpCategory,BigDecimal transferorCharge, BigDecimal transfereeCharge, BigDecimal changeMsisdnCharge,BigDecimal maxAmtPerTrans,
			String publicityId,Integer maxFnfserviceNumbers, Integer maxSmsserviceNumbers,String caServicePackageYn, String atpCategoryByOffer, String description, String chargeOnFirstUsageYn,
			String allowMultipleAtpYn, String userDefined1, String userDefined2, String userDefined3) {

		String sql = """
				INSERT INTO CS_RAT_SERVICE_PACKAGE
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
				    CHARGE_ON_FIRST_USAGE_YN,
				    ALLOW_MULTIPLE_ATP_YN,
				    USER_DEFINED_1,
				    USER_DEFINED_2,
				    USER_DEFINED_3
				)
				VALUES
				(
				    ?,
				    UPPER(?),
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    TO_DATE(?, 'MM/DD/YYYY'),
				    ?,

				    ?,

				    ?,
				    ?,
				    ?,
				    ?,

				    ?,

				    ?,
				    ?,

				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?
				)
				""";

		oracleJdbcTemplate.update(sql,

				servicePackageId, servicePackageDesc,rentalAmount, activationCharge,networkId,tax1, tax2, tax3,chargeId,addPackYn,rentalType, rentalPeriod,aspType, endDate,serviceDuration,
                     atpCategory,transferorCharge, transfereeCharge, changeMsisdnCharge, maxAmtPerTrans,publicityId,maxFnfserviceNumbers, maxSmsserviceNumbers,caServicePackageYn, atpCategoryByOffer, description, chargeOnFirstUsageYn, allowMultipleAtpYn,
                                  userDefined1, userDefined2, userDefined3);
	}

	/**
	 * Maps a created service plan to the tariff plan.
	 *
	 * Used for normal ASP type U.
	 *
	 * Procedure: INSERT INTO CS_RAT_SERVICE_PLAN_PACKAGE
	 */
	public void insertServicePlanPackageMapping(Long servicePackageId, Long servicePlanId, Long networkId) {

		String sql = """
				INSERT INTO CS_RAT_SERVICE_PLAN_PACKAGE
				(
				    SERVICE_PACKAGE_ID,
				    SERVICE_PLAN_ID,
				    NETWORK_ID
				)
				VALUES
				(
				    ?,
				    ?,
				    ?
				)
				""";

		oracleJdbcTemplate.update(sql, servicePackageId, servicePlanId, networkId);
	}

	/**
	 * Used for bonus/discount service package.
	 *
	 * Procedure: INSERT INTO CS_ADD_SVCPACK_BON_DISC_MAP
	 */
	public void insertBonusDiscountMapping(Long servicePackageId, Long servicePlanId, Long networkId, String aspType) {

		String sql = """
				INSERT INTO CS_ADD_SVCPACK_BON_DISC_MAP
				(
				    NETWORK_ID,
				    SERVICE_PACKAGE_ID,
				    BONUS_DISC_PLAN_ID,
				    ASP_TYPE
				)
				VALUES
				(
				    ?,
				    ?,
				    ?,
				    ?
				)
				""";

		oracleJdbcTemplate.update(sql, networkId, servicePackageId, servicePlanId, aspType);
	}

	/**
	 * Gets rating_service_yn and service name for a basic service.
	 */
	public BasicServiceInfo getBasicServiceInfo(Long basicServiceId) {

		String sql = """
				SELECT
				    RATING_SERVICE_YN,
				    SERVICE_NAME
				FROM CS_RAT_MT_BASIC_SERVICE
				WHERE BASIC_SERVICE_ID = ?
				""";

		return oracleJdbcTemplate.queryForObject(sql,
				(rs, rowNum) -> new BasicServiceInfo(rs.getString("RATING_SERVICE_YN"), rs.getString("SERVICE_NAME")),
				basicServiceId);
	}

	/**
	 * Gets the service plan type for a basic + derived service.
	 *
	 * Procedure: SELECT SERVICE_PLAN_TYPE_ID FROM CS_RAT_SVCPLAN_SERVICES_MAP WHERE
	 * BASIC_SERVICE_ID = ? AND DERIVED_SERVICE_ID = ?
	 */
	public Long getServicePlanTypeForBasicDerived(Long basicServiceId, Long derivedServiceId) {

		String sql = """
				SELECT SERVICE_PLAN_TYPE_ID
				FROM CS_RAT_SVCPLAN_SERVICES_MAP
				WHERE BASIC_SERVICE_ID = ?
				  AND DERIVED_SERVICE_ID = ?
				""";

		return oracleJdbcTemplate.queryForObject(sql, Long.class, basicServiceId, derivedServiceId);
	}

	/**
	 * Gets service plan type for a non-rating basic service.
	 */
	public Long getServicePlanTypeForBasic(Long basicServiceId) {

		String sql = """
				SELECT SERVICE_PLAN_TYPE_ID
				FROM CS_RAT_SVCPLAN_SERVICES_MAP
				WHERE BASIC_SERVICE_ID = ?
				""";

		return oracleJdbcTemplate.queryForObject(sql, Long.class, basicServiceId);
	}

	/**
	 * Gets derived service name for validation/error reporting.
	 */
	public String getDerivedServiceName(Long derivedServiceId) {

		String sql = """
				SELECT SERVICE_NAME
				FROM CS_RAT_MT_DERIVED_SERVICE
				WHERE DERIVED_SERVICE_ID = ?
				""";

		return oracleJdbcTemplate.queryForObject(sql, String.class, derivedServiceId);
	}

	public void insertServiceAtpMapping(Long networkId, Long servicePackageId, Long basicServiceId,
			Long derivedServiceId) {

		String sql = """
				INSERT INTO CS_RAT_SERVICE_ATP_MAP
				(
				    NETWORK_ID,
				    SERVICE_PACKAGE_ID,
				    BASIC_SERVICE_ID,
				    DERIVED_SERVICE_ID
				)
				VALUES
				(
				    ?,
				    ?,
				    ?,
				    ?
				)
				""";

		oracleJdbcTemplate.update(sql, networkId, servicePackageId, basicServiceId, derivedServiceId);
	}

	/**
	 * Removes only the Tariff-Plan<->service-plan mapping row (used when a
	 * balance category / its service plan is removed on Modify ATP). The
	 * service plan row itself is left untouched.
	 */
	public void deleteServicePlanPackageMapping(Long servicePackageId, Long servicePlanId) {

		String sql = """
				DELETE FROM CS_RAT_SERVICE_PLAN_PACKAGE
				WHERE SERVICE_PACKAGE_ID = ?
				  AND SERVICE_PLAN_ID = ?
				""";

		oracleJdbcTemplate.update(sql, servicePackageId, servicePlanId);
	}

	/**
	 * Removes only a single basic/derived service <-> Tariff Plan mapping
	 * row (used when the user removes a derivedServiceSelection entry on
	 * Modify ATP).
	 */
	public void deleteServiceAtpMapping(Long servicePackageId, Long basicServiceId, Long derivedServiceId) {

		String sql = """
				DELETE FROM CS_RAT_SERVICE_ATP_MAP
				WHERE SERVICE_PACKAGE_ID = ?
				  AND BASIC_SERVICE_ID = ?
				  AND DERIVED_SERVICE_ID = ?
				""";

		oracleJdbcTemplate.update(sql, servicePackageId, basicServiceId, derivedServiceId);
	}

	public record BasicServiceInfo(String ratingServiceYn, String serviceName) {
	}

	public List<Long> findTariffPlanIdsByServicePlanIds(List<Long> servicePlanIds) {

    if (servicePlanIds == null || servicePlanIds.isEmpty()) {
        return Collections.emptyList();
    }

    String placeholders = servicePlanIds.stream()
            .map(id -> "?")
            .collect(Collectors.joining(","));

    String sql = """
            SELECT DISTINCT spp.SERVICE_PACKAGE_ID
            FROM CS_RAT_SERVICE_PLAN_PACKAGE spp
            JOIN CS_RAT_SERVICE_PACKAGE sp
              ON sp.SERVICE_PACKAGE_ID = spp.SERVICE_PACKAGE_ID
            WHERE spp.SERVICE_PLAN_ID IN (
            """ + placeholders + """
            )
            AND sp.ADD_PACK_YN = 'N'
            """;

    return oracleJdbcTemplate.queryForList(
            sql,
            Long.class,
            servicePlanIds.toArray()
    );
}
}