package com.xius.TariffBuilder.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.xius.TariffBuilder.Entity.PlanSubscriptionRule;

/**
 * Repository for PLAN_SUBSCRIPTION_RULES.
 *
 * IMPORTANT: JdbcTemplate ONLY. No JPA Entity / Spring Data Repository is used.
 * All SQL is written out directly, matching the two INSERT statements present
 * in pro_plan_subscrib_rules_config for pi_action_flag = 'ADD'.
 */
@Repository
public class PlanSubscriptionRuleRepository {

    private static final Logger logger = LoggerFactory.getLogger(PlanSubscriptionRuleRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public PlanSubscriptionRuleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /*
     * Mirrors:
     *   INSERT INTO plan_subscription_rules
     *      (network_id, plan_id, criteria_type, include_atp_list,
     *       exclude_atp_list, to_be_added_list, to_be_removed_list,
     *       include_list_rule_type, exclude_list_rule_type, extend_account_validity)
     *   VALUES (pi_network_id, pi_plan_id, 'ATP', lv_include_list,
     *           lv_exclude_list, lv_to_be_added_list, lv_to_be_removed_list,
     *           lv_include_all_any_flag, lv_exclude_all_any_flag, pi_extend_account_validity)
     */
    public int insertAtpRule(PlanSubscriptionRule rule) {

        String sql = "INSERT INTO PLAN_SUBSCRIPTION_RULES "
                + "(NETWORK_ID, PLAN_ID, CRITERIA_TYPE, INCLUDE_ATP_LIST, EXCLUDE_ATP_LIST, "
                + "TO_BE_ADDED_LIST, TO_BE_REMOVED_LIST, INCLUDE_LIST_RULE_TYPE, "
                + "EXCLUDE_LIST_RULE_TYPE, EXTEND_ACCOUNT_VALIDITY) "
                + "VALUES (?, ?, 'ATP', ?, ?, ?, ?, ?, ?, ?)";

        logger.info("Inserting ATP plan subscription rule networkId={} planId={}",
                rule.getNetworkId(), rule.getPlanId());

        return jdbcTemplate.update(sql,
                rule.getNetworkId(),
                rule.getPlanId(),
                rule.getIncludeAtpList(),
                rule.getExcludeAtpList(),
                rule.getToBeAddedList(),
                rule.getToBeRemovedList(),
                rule.getIncludeListRuleType(),
                rule.getExcludeListRuleType(),
                rule.getExtendAccountValidity());
    }

    /*
     * Mirrors:
     *   INSERT INTO plan_subscription_rules
     *      (network_id, plan_id, criteria_type, include_atp_list,
     *       exclude_atp_list, to_be_added_list, to_be_removed_list)
     *   VALUES (pi_network_id, pi_plan_id, 'TP', lv_tp_include_list,
     *           lv_tp_exclude_list, NULL, NULL)
     */
    public int insertTpRule(Long networkId, Long planId, String tpIncludeList, String tpExcludeList) {

        String sql = "INSERT INTO PLAN_SUBSCRIPTION_RULES "
                + "(NETWORK_ID, PLAN_ID, CRITERIA_TYPE, INCLUDE_ATP_LIST, EXCLUDE_ATP_LIST, "
                + "TO_BE_ADDED_LIST, TO_BE_REMOVED_LIST) "
                + "VALUES (?, ?, 'TP', ?, ?, NULL, NULL)";

        logger.info("Inserting TP plan subscription rule networkId={} planId={}", networkId, planId);

        return jdbcTemplate.update(sql, networkId, planId, tpIncludeList, tpExcludeList);
    }

    // =====================================================================
    // VIEW support
    // =====================================================================

    /*
     * Mirrors:
     *   BEGIN
     *      SELECT include_atp_list, exclude_atp_list, to_be_added_list,
     *             to_be_removed_list, b.service_package_desc,
     *             a.include_list_rule_type, a.exclude_list_rule_type,
     *             extend_account_validity
     *        INTO ...
     *        FROM plan_subscription_rules a, cs_rat_service_package b
     *       WHERE a.plan_id = b.service_package_id
     *         AND a.plan_id = pi_plan_id
     *         AND a.network_id = pi_network_id
     *         AND criteria_type = 'ATP';
     *   EXCEPTION WHEN NO_DATA_FOUND THEN NULL;
     *   END;
     *
     * Returns Optional.empty() when no row is found - equivalent to the
     * procedure's WHEN NO_DATA_FOUND THEN NULL (i.e. it does NOT propagate
     * as a failure), unlike the per-id lookups below.
     */
    public Optional<Map<String, Object>> findAtpRuleWithPlanName(Long planId, Long networkId) {

        String sql = "SELECT a.INCLUDE_ATP_LIST AS INCLUDE_ATP_LIST, "
                + "a.EXCLUDE_ATP_LIST AS EXCLUDE_ATP_LIST, "
                + "a.TO_BE_ADDED_LIST AS TO_BE_ADDED_LIST, "
                + "a.TO_BE_REMOVED_LIST AS TO_BE_REMOVED_LIST, "
                + "b.SERVICE_PACKAGE_DESC AS SERVICE_PACKAGE_DESC, "
                + "a.INCLUDE_LIST_RULE_TYPE AS INCLUDE_LIST_RULE_TYPE, "
                + "a.EXCLUDE_LIST_RULE_TYPE AS EXCLUDE_LIST_RULE_TYPE, "
                + "a.EXTEND_ACCOUNT_VALIDITY AS EXTEND_ACCOUNT_VALIDITY "
                + "FROM PLAN_SUBSCRIPTION_RULES a, CS_RAT_SERVICE_PACKAGE b "
                + "WHERE a.PLAN_ID = b.SERVICE_PACKAGE_ID "
                + "AND a.PLAN_ID = ? AND a.NETWORK_ID = ? AND a.CRITERIA_TYPE = 'ATP'";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, planId, networkId);

        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /*
     * Mirrors:
     *   BEGIN
     *      SELECT include_atp_list, exclude_atp_list
     *        INTO lv_tp_include_list, lv_tp_exclude_list
     *        FROM plan_subscription_rules
     *       WHERE plan_id = pi_plan_id AND network_id = pi_network_id AND criteria_type = 'TP';
     *   EXCEPTION WHEN NO_DATA_FOUND THEN NULL;
     *   END;
     */
    public Optional<Map<String, Object>> findTpRule(Long planId, Long networkId) {

        String sql = "SELECT INCLUDE_ATP_LIST AS INCLUDE_ATP_LIST, EXCLUDE_ATP_LIST AS EXCLUDE_ATP_LIST "
                + "FROM PLAN_SUBSCRIPTION_RULES "
                + "WHERE PLAN_ID = ? AND NETWORK_ID = ? AND CRITERIA_TYPE = 'TP'";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, planId, networkId);

        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /*
     * Mirrors (used inside the ATP include/exclude/to-be-added/to-be-removed
     * loops):
     *   SELECT service_package_desc, service_package_id
     *     INTO lv_atp_name / lv_tp_name, lv_atp_id / lv_tp_id
     *     FROM cs_rat_service_package
     *    WHERE service_package_id = lv_values(i);
     *
     * NOTE: intentionally NOT wrapped with NO_DATA_FOUND handling here -
     * exactly like the procedure, if the id doesn't exist this throws
     * (EmptyResultDataAccessException), which propagates up to the outer
     * WHEN OTHERS equivalent in the Service layer.
     */
    public Map<String, Object> findServicePackageNameAndId(String servicePackageId) {

        String sql = "SELECT SERVICE_PACKAGE_DESC AS SERVICE_PACKAGE_DESC, "
                + "SERVICE_PACKAGE_ID AS SERVICE_PACKAGE_ID "
                + "FROM CS_RAT_SERVICE_PACKAGE WHERE SERVICE_PACKAGE_ID = ?";

        return jdbcTemplate.queryForMap(sql, servicePackageId);
    }

    /*
     * Mirrors (used inside the TP include/exclude loops):
     *   SELECT tariff_package_desc, tariff_package_id
     *     INTO lv_tp_name, lv_tp_id
     *     FROM cs_rat_tariff_package
     *    WHERE tariff_package_id = lv_values(i);
     *
     * Same as above - no local NO_DATA_FOUND handling, propagates on failure.
     */
    public Map<String, Object> findTariffPackageNameAndId(String tariffPackageId) {

        String sql = "SELECT TARIFF_PACKAGE_DESC AS TARIFF_PACKAGE_DESC, "
                + "TARIFF_PACKAGE_ID AS TARIFF_PACKAGE_ID "
                + "FROM CS_RAT_TARIFF_PACKAGE WHERE TARIFF_PACKAGE_ID = ?";

        return jdbcTemplate.queryForMap(sql, tariffPackageId);
    }

    // =====================================================================
    // MODIFY support
    // =====================================================================

    /*
     * Mirrors:
     *   UPDATE plan_subscription_rules
     *      SET include_atp_list = ?, exclude_atp_list = ?, to_be_added_list = ?,
     *          to_be_removed_list = ?, include_list_rule_type = ?,
     *          exclude_list_rule_type = ?, extend_account_validity = ?
     *    WHERE network_id = ? AND plan_id = ? AND criteria_type = 'ATP';
     *
     * Executed unconditionally, exactly as in the procedure (no null guard).
     */
    public int updateAtpRule(PlanSubscriptionRule rule) {

        String sql = "UPDATE PLAN_SUBSCRIPTION_RULES SET "
                + "INCLUDE_ATP_LIST = ?, EXCLUDE_ATP_LIST = ?, TO_BE_ADDED_LIST = ?, "
                + "TO_BE_REMOVED_LIST = ?, INCLUDE_LIST_RULE_TYPE = ?, EXCLUDE_LIST_RULE_TYPE = ?, "
                + "EXTEND_ACCOUNT_VALIDITY = ? "
                + "WHERE NETWORK_ID = ? AND PLAN_ID = ? AND CRITERIA_TYPE = 'ATP'";

        logger.info("Updating ATP plan subscription rule networkId={} planId={}",
                rule.getNetworkId(), rule.getPlanId());

        return jdbcTemplate.update(sql,
                rule.getIncludeAtpList(),
                rule.getExcludeAtpList(),
                rule.getToBeAddedList(),
                rule.getToBeRemovedList(),
                rule.getIncludeListRuleType(),
                rule.getExcludeListRuleType(),
                rule.getExtendAccountValidity(),
                rule.getNetworkId(),
                rule.getPlanId());
    }

    /*
     * Mirrors the DUP_VAL_ON_INDEX branch of MODIFY:
     *   UPDATE plan_subscription_rules
     *      SET include_atp_list = ?, exclude_atp_list = ?
     *    WHERE network_id = ? AND plan_id = ? AND criteria_type = 'TP';
     */
    public int updateTpRule(Long networkId, Long planId, String tpIncludeList, String tpExcludeList) {

        String sql = "UPDATE PLAN_SUBSCRIPTION_RULES SET INCLUDE_ATP_LIST = ?, EXCLUDE_ATP_LIST = ? "
                + "WHERE NETWORK_ID = ? AND PLAN_ID = ? AND CRITERIA_TYPE = 'TP'";

        logger.info("Updating TP plan subscription rule networkId={} planId={}", networkId, planId);

        return jdbcTemplate.update(sql, tpIncludeList, tpExcludeList, networkId, planId);
    }

    /*
     * Mirrors the ELSE branch of MODIFY's TP handling:
     *   DELETE FROM plan_subscription_rules
     *         WHERE network_id = pi_network_id AND plan_id = pi_plan_id AND criteria_type = 'TP';
     */
    public int deleteTpRule(Long networkId, Long planId) {

        String sql = "DELETE FROM PLAN_SUBSCRIPTION_RULES WHERE NETWORK_ID = ? AND PLAN_ID = ? AND CRITERIA_TYPE = 'TP'";

        logger.info("Deleting TP plan subscription rule networkId={} planId={}", networkId, planId);

        return jdbcTemplate.update(sql, networkId, planId);
    }

    // =====================================================================
    // DELETE support
    // =====================================================================

    /*
     * Mirrors:
     *   DELETE FROM plan_subscription_rules
     *         WHERE plan_id = pi_plan_id AND network_id = pi_network_id;
     *
     * Deletes BOTH the ATP and TP rows for the plan in one statement, exactly
     * as the procedure does (no criteria_type filter).
     */
    public int deleteAllRulesForPlan(Long networkId, Long planId) {

        String sql = "DELETE FROM PLAN_SUBSCRIPTION_RULES WHERE PLAN_ID = ? AND NETWORK_ID = ?";

        logger.info("Deleting all plan subscription rules networkId={} planId={}", networkId, planId);

        return jdbcTemplate.update(sql, planId, networkId);
    }
}