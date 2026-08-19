package com.xius.Lb.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class AtpRepository {

	private final JdbcTemplate oracleJdbcTemplate;

	public AtpRepository(JdbcTemplate oracleJdbcTemplate) {

		this.oracleJdbcTemplate = oracleJdbcTemplate;
	}

	/**
	 * Check whether ATP/service package already exists.
	 *
	 * Procedure:
	 *
	 * WHERE network_id = pi_network_id AND UPPER(service_package_desc) =
	 * UPPER(pi_service_package_desc)
	 */
	public boolean existsAtp(Long networkId, String atpName) {

		String sql = """
				SELECT COUNT(1)
				FROM cs_rat_service_package
				WHERE network_id = ?
				  AND UPPER(service_package_desc) =
				      UPPER(?)
				""";

		Integer count = oracleJdbcTemplate.queryForObject(sql, Integer.class, networkId, atpName);

		return count != null && count > 0;
	}

	/**
	 * Generate ATP/service-package ID.
	 *
	 * Procedure:
	 *
	 * seq_service_pack_id.NEXTVAL
	 */
	public Long getNextAtpId() {

		String sql = """
				SELECT seq_service_pack_id.NEXTVAL
				FROM dual
				""";

		return oracleJdbcTemplate.queryForObject(sql, Long.class);
	}

	/**
	 * Publicity ID validation.
	 *
	 * Procedure checks this for PDP/VDP.
	 */
	public boolean existsPublicityId(String publicityId) {

		String sql = """
				SELECT COUNT(1)
				FROM cs_rat_service_package
				WHERE publicity_id = ?
				""";

		Integer count = oracleJdbcTemplate.queryForObject(sql, Integer.class, publicityId);

		return count != null && count > 0;
	}

	/**
	 * Insert ATP/service package.
	 *
	 * This follows the ADD INSERT from:
	 *
	 * pro_manage_svcpkg_svcplan_map
	 */
	public void insertAtp(Long atpId, String atpName, BigDecimal rentalAmount, BigDecimal activationCharge,
			Long networkId,

			Integer tax1, Integer tax2, Integer tax3, String chargeId, String addPackYn, String rentalType,

			Integer rentalPeriod, String aspType, String validTo, Integer serviceDuration,

			String atpCategory, String publicityId, String atpCategoryByOffer, String description,
			String chargeOnFirstUsageYn) {

		String sql = """
				INSERT INTO cs_rat_service_package
				(
				    service_package_id,
				    service_package_desc,
				    rental_amount,
				    activation_charge,
				    network_id,
				    tax1,
				    tax2,
				    tax3,
				    charge_id,
				    add_pack_yn,
				    rental_type,
				    rental_period,
				    asp_type,
				    end_date,
				    service_duration,
				    atp_category,
				    publicity_id,
				    atp_category_by_offer,
				    description,
				    charge_on_first_usage_yn
				)
				VALUES
				(
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
				    TO_DATE(?, 'MM/DD/YYYY'),
				    ?,
				    ?,
				    ?,
				    ?,
				    ?,
				    ?
				)
				""";

		oracleJdbcTemplate.update(sql, atpId, atpName, rentalAmount, activationCharge, networkId, tax1, tax2, tax3,
				chargeId, addPackYn, rentalType, rentalPeriod, aspType, validTo, serviceDuration, atpCategory,
				publicityId, atpCategoryByOffer, description, chargeOnFirstUsageYn);
	}

	/**
	 * Get type_of_service for every created service plan.
	 *
	 * Procedure:
	 *
	 * SELECT type_of_service FROM cs_rat_service_plans WHERE service_plan_id IN
	 * (...)
	 */
	public List<Integer> getServicePlanTypes(List<Long> servicePlanIds) {

		if (servicePlanIds == null || servicePlanIds.isEmpty()) {

			return Collections.emptyList();
		}

		String placeholders = servicePlanIds.stream().map(id -> "?").collect(Collectors.joining(","));

		String sql = """
				SELECT type_of_service
				FROM cs_rat_service_plans
				WHERE service_plan_id IN (
				""" + placeholders + ")";

		return oracleJdbcTemplate.queryForList(sql, Integer.class, servicePlanIds.toArray());
	}

	/**
	 * Get service plan description for validation.
	 */
	public String getServicePlanDescription(Long servicePlanId) {

		String sql = """
				SELECT service_plan_desc
				FROM cs_rat_service_plans
				WHERE service_plan_id = ?
				""";

		return oracleJdbcTemplate.queryForObject(sql, String.class, servicePlanId);
	}

	/**
	 * Map service plan to ATP.
	 *
	 * Procedure:
	 *
	 * IF pi_asp_type = 'U' INSERT INTO cs_rat_service_plan_package
	 */
	public void insertServicePlanMapping(Long atpId, Long servicePlanId, Long networkId) {

		String sql = """
				INSERT INTO cs_rat_service_plan_package
				(
				    service_package_id,
				    service_plan_id,
				    network_id
				)
				VALUES
				(
				    ?,
				    ?,
				    ?
				)
				""";

		oracleJdbcTemplate.update(sql, atpId, servicePlanId, networkId);
	}

	/**
	 * Basic service details.
	 */
	public BasicServiceInfo getBasicServiceInfo(Long basicServiceId) {

		String sql = """
				SELECT rating_service_yn,
				       service_name
				FROM cs_rat_mt_basic_service
				WHERE basic_service_id = ?
				""";

		return oracleJdbcTemplate.queryForObject(sql,
				(rs, rowNum) -> new BasicServiceInfo(rs.getString("rating_service_yn"), rs.getString("service_name")),
				basicServiceId);
	}

	/**
	 * Get service-plan type for rating service.
	 */
	public Long getServicePlanTypeForBasicDerived(Long basicServiceId, Long derivedServiceId) {

		String sql = """
				SELECT service_plan_type_id
				FROM cs_rat_svcplan_services_map
				WHERE basic_service_id = ?
				  AND derived_service_id = ?
				""";

		return oracleJdbcTemplate.queryForObject(sql, Long.class, basicServiceId, derivedServiceId);
	}

	/**
	 * Get service-plan type for non-rating service.
	 */
	public Long getServicePlanTypeForBasic(Long basicServiceId) {

		String sql = """
				SELECT service_plan_type_id
				FROM cs_rat_svcplan_services_map
				WHERE basic_service_id = ?
				""";

		return oracleJdbcTemplate.queryForObject(sql, Long.class, basicServiceId);
	}

	/**
	 * Get derived service name.
	 */
	public String getDerivedServiceName(Long derivedServiceId) {

		String sql = """
				SELECT service_name
				FROM cs_rat_mt_derived_service
				WHERE derived_service_id = ?
				""";

		return oracleJdbcTemplate.queryForObject(sql, String.class, derivedServiceId);
	}

	/**
	 * Map basic/derived service to ATP.
	 *
	 * Procedure:
	 *
	 * INSERT INTO cs_rat_service_atp_map
	 */
	public void insertServiceAtpMapping(Long networkId, Long atpId, Long basicServiceId, Long derivedServiceId) {

		String sql = """
				INSERT INTO cs_rat_service_atp_map
				(
				    network_id,
				    service_package_id,
				    basic_service_id,
				    derived_service_id
				)
				VALUES
				(
				    ?,
				    ?,
				    ?,
				    ?
				)
				""";

		oracleJdbcTemplate.update(sql, networkId, atpId, basicServiceId, derivedServiceId);
	}

	/**
	 * Map bundle to ATP.
	 *
	 * Mirrors the legacy clone-flow insert in
	 * TariffBuilder.UserService.BundleService#cloneAtpData — same table,
	 * same PLAN_TYPE='B' convention for a bundle-type mapping.
	 */
	public void insertAtpBundleMapping(Long atpId, Long bundleId, Long networkId) {

		String sql = """
				INSERT INTO CS_ATP_ACCUMU_BON_DISC_MAP
				(
				    BUNDLE_OR_DISCOUNT_ID,
				    ATP_ID,
				    PLAN_TYPE,
				    NETWORK_ID
				)
				VALUES
				(
				    ?,
				    ?,
				    'B',
				    ?
				)
				""";

		oracleJdbcTemplate.update(sql, bundleId, atpId, networkId);
	}

	/**
	 * Field-level update of the ATP/service package core fields. Row is
	 * never dropped/recreated on Modify ATP.
	 */
	public void updateAtp(Long atpId, String atpName, String validTo, String atpCategoryByOffer, String description,
			String publicityId) {

		String sql = """
				UPDATE cs_rat_service_package
				SET service_package_desc = ?,
				    end_date = TO_DATE(?, 'MM/DD/YYYY'),
				    atp_category_by_offer = ?,
				    description = ?,
				    publicity_id = ?
				WHERE service_package_id = ?
				""";

		oracleJdbcTemplate.update(sql, atpName, validTo, atpCategoryByOffer, description, publicityId, atpId);
	}

	/**
	 * Removes only the ATP<->service-plan mapping row (used when a balance
	 * category / its service plan is removed on Modify ATP). The service
	 * plan row itself is left untouched.
	 */
	public void deleteServicePlanMapping(Long atpId, Long servicePlanId) {

		String sql = """
				DELETE FROM cs_rat_service_plan_package
				WHERE service_package_id = ?
				  AND service_plan_id = ?
				""";

		oracleJdbcTemplate.update(sql, atpId, servicePlanId);
	}

	/**
	 * Removes only a single basic/derived service <-> ATP mapping row (used
	 * when the user removes a derivedServiceSelection entry on Modify ATP).
	 */
	public void deleteServiceAtpMapping(Long atpId, Long basicServiceId, Long derivedServiceId) {

		String sql = """
				DELETE FROM cs_rat_service_atp_map
				WHERE service_package_id = ?
				  AND basic_service_id = ?
				  AND derived_service_id = ?
				""";

		oracleJdbcTemplate.update(sql, atpId, basicServiceId, derivedServiceId);
	}

	public record BasicServiceInfo(String ratingServiceYn, String serviceName) {
	}
}