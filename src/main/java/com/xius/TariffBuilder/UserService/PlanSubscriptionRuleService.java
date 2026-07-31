package com.xius.TariffBuilder.UserService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xius.TariffBuilder.Dto.PlanSubscriptionRuleAddRequestDto;
import com.xius.TariffBuilder.Dto.PlanSubscriptionRuleKeyDto;
import com.xius.TariffBuilder.Dto.PlanSubscriptionRuleResponseDto;
import com.xius.TariffBuilder.Dto.PlanSubscriptionRuleViewResponseDto;
import com.xius.TariffBuilder.Entity.PlanSubscriptionRule;
import com.xius.TariffBuilder.repository.PlanSubscriptionRuleRepository;

@Service
public class PlanSubscriptionRuleService {

    private static final Logger logger = LoggerFactory.getLogger(PlanSubscriptionRuleService.class);

    private static final String SEPARATOR_TILDE = "~";
    private static final String INCLUDE_FLAG = "I";

    private final PlanSubscriptionRuleRepository repository;

    public PlanSubscriptionRuleService(PlanSubscriptionRuleRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PlanSubscriptionRuleResponseDto addPlanSubscriptionRule(PlanSubscriptionRuleAddRequestDto dto) {

        int errorCode = 0;
        String errorDesc = "SUCCESS";

        try {
            logger.info("ADD plan subscription rule started networkId={} planId={}",
                    dto.getNetworkId(), dto.getPlanId());

           
            List<String> includeIds = new ArrayList<>();
            List<String> excludeIds = new ArrayList<>();
            String includeAllAnyFlag = null;
            String excludeAllAnyFlag = null;

            if (dto.getAtpIncludeExcludeList() != null && !dto.getAtpIncludeExcludeList().isEmpty()) {
                for (String element : dto.getAtpIncludeExcludeList()) {

                    String[] values = splitBySeparator(element, SEPARATOR_TILDE);

                    if (INCLUDE_FLAG.equals(values[1])) {
                        includeIds.add(values[0]);
                        // lv_include_all_any_flag := lv_values(3) -> overwritten every
                        // iteration, final value = last 'I' element's 3rd part.
                        includeAllAnyFlag = values[2];
                    } else {
                        excludeIds.add(values[0]);
                        // lv_exclude_all_any_flag := lv_values(3) -> same, last 'E' wins.
                        excludeAllAnyFlag = values[2];
                    }
                }
            }

            // ---------------------------------------------------------------
            // lv_to_be_added_list build-up
            // IF pi_atp_to_be_added_list IS NOT NULL AND .COUNT > 0 THEN
            //    FOR i IN 1 .. pi_atp_to_be_added_list.COUNT LOOP
            //       lv_to_be_added_list := lv_to_be_added_list || ',' || pi_atp_to_be_added_list(i)
            // ---------------------------------------------------------------
            String toBeAddedList = joinIfPresent(dto.getAtpToBeAddedList());

            // ---------------------------------------------------------------
            // lv_to_be_removed_list build-up (same pattern as above)
            // ---------------------------------------------------------------
            String toBeRemovedList = joinIfPresent(dto.getAtpToBeRemovedList());

            String includeList = includeIds.isEmpty() ? null : String.join(",", includeIds);
            String excludeList = excludeIds.isEmpty() ? null : String.join(",", excludeIds);

            // ---------------------------------------------------------------
            // IF (lv_include_list IS NOT NULL OR lv_exclude_list IS NOT NULL
            //     OR lv_to_be_added_list IS NOT NULL OR lv_to_be_removed_list IS NOT NULL)
            // THEN INSERT INTO plan_subscription_rules (... criteria_type='ATP' ...)
            // ---------------------------------------------------------------
            if (includeList != null || excludeList != null || toBeAddedList != null || toBeRemovedList != null) {

                PlanSubscriptionRule atpRule = new PlanSubscriptionRule();
                atpRule.setNetworkId(dto.getNetworkId());
                atpRule.setPlanId(dto.getPlanId());
                atpRule.setCriteriaType("ATP");
                atpRule.setIncludeAtpList(includeList);
                atpRule.setExcludeAtpList(excludeList);
                atpRule.setToBeAddedList(toBeAddedList);
                atpRule.setToBeRemovedList(toBeRemovedList);
                atpRule.setIncludeListRuleType(includeAllAnyFlag);
                atpRule.setExcludeListRuleType(excludeAllAnyFlag);
                atpRule.setExtendAccountValidity(dto.getExtendAccountValidity());

                repository.insertAtpRule(atpRule);
            }

        
            if (dto.getTpIncludeExcludeList() != null && !dto.getTpIncludeExcludeList().isEmpty()) {

                List<String> tpIncludeIds = new ArrayList<>();
                List<String> tpExcludeIds = new ArrayList<>();

                for (String element : dto.getTpIncludeExcludeList()) {

                    String[] values = splitBySeparator(element, SEPARATOR_TILDE);

                    if (INCLUDE_FLAG.equals(values[1])) {
                        tpIncludeIds.add(values[0]);
                    } else {
                        tpExcludeIds.add(values[0]);
                    }
                }

                String tpIncludeList = tpIncludeIds.isEmpty() ? null : String.join(",", tpIncludeIds);
                String tpExcludeList = tpExcludeIds.isEmpty() ? null : String.join(",", tpExcludeIds);

                repository.insertTpRule(dto.getNetworkId(), dto.getPlanId(), tpIncludeList, tpExcludeList);
            }

            logger.info("ADD plan subscription rule completed successfully networkId={} planId={}",
                    dto.getNetworkId(), dto.getPlanId());

        } catch (Exception e) {
            // EXCEPTION WHEN OTHERS THEN
            //    po_error_code := SQLCODE;
            //    po_error_desc := SUBSTR(DBMS_UTILITY.format_error_backtrace || '---->' || SQLERRM, 1, 1000);
            errorCode = -1;
            errorDesc = buildErrorDesc(e);
            logger.error("ADD plan subscription rule failed networkId={} planId={} error={}",
                    dto.getNetworkId(), dto.getPlanId(), errorDesc, e);
        }

        return new PlanSubscriptionRuleResponseDto(errorCode, errorDesc);
    }

    @Transactional
    public PlanSubscriptionRuleResponseDto modifyPlanSubscriptionRule(PlanSubscriptionRuleAddRequestDto dto) {

        int errorCode = 0;
        String errorDesc = "SUCCESS";

        try {
            logger.info("MODIFY plan subscription rule started networkId={} planId={}",
                    dto.getNetworkId(), dto.getPlanId());

            // ---------------------------------------------------------------
            // Same split/build logic as ADD for the ATP include/exclude,
            // to-be-added and to-be-removed lists.
            // ---------------------------------------------------------------
            List<String> includeIds = new ArrayList<>();
            List<String> excludeIds = new ArrayList<>();
            String includeAllAnyFlag = null;
            String excludeAllAnyFlag = null;

            if (dto.getAtpIncludeExcludeList() != null && !dto.getAtpIncludeExcludeList().isEmpty()) {
                for (String element : dto.getAtpIncludeExcludeList()) {

                    String[] values = splitBySeparator(element, SEPARATOR_TILDE);

                    if (INCLUDE_FLAG.equals(values[1])) {
                        includeIds.add(values[0]);
                        includeAllAnyFlag = values[2];
                    } else {
                        excludeIds.add(values[0]);
                        excludeAllAnyFlag = values[2];
                    }
                }
            }

            String toBeAddedList = joinIfPresent(dto.getAtpToBeAddedList());
            String toBeRemovedList = joinIfPresent(dto.getAtpToBeRemovedList());
            String includeList = includeIds.isEmpty() ? null : String.join(",", includeIds);
            String excludeList = excludeIds.isEmpty() ? null : String.join(",", excludeIds);

            // ---------------------------------------------------------------
            // UPDATE plan_subscription_rules SET ... WHERE ... criteria_type='ATP'
            // Executed unconditionally, exactly like the procedure (no null guard
            // around this UPDATE, unlike the ADD's INSERT).
            // ---------------------------------------------------------------
            PlanSubscriptionRule atpRule = new PlanSubscriptionRule();
            atpRule.setNetworkId(dto.getNetworkId());
            atpRule.setPlanId(dto.getPlanId());
            atpRule.setIncludeAtpList(includeList);
            atpRule.setExcludeAtpList(excludeList);
            atpRule.setToBeAddedList(toBeAddedList);
            atpRule.setToBeRemovedList(toBeRemovedList);
            atpRule.setIncludeListRuleType(includeAllAnyFlag);
            atpRule.setExcludeListRuleType(excludeAllAnyFlag);
            atpRule.setExtendAccountValidity(dto.getExtendAccountValidity());

            repository.updateAtpRule(atpRule);

            // ---------------------------------------------------------------
            // IF pi_tp_include_exclude_list IS NOT NULL AND .COUNT > 0 THEN
            //    ... build tp include/exclude lists ...
            //    BEGIN
            //       INSERT INTO plan_subscription_rules (... criteria_type='TP' ...)
            //    EXCEPTION WHEN DUP_VAL_ON_INDEX THEN
            //       UPDATE plan_subscription_rules SET ... WHERE ... criteria_type='TP'
            //    END;
            // ELSE
            //    DELETE FROM plan_subscription_rules WHERE ... criteria_type='TP'
            // END IF;
            // ---------------------------------------------------------------
            if (dto.getTpIncludeExcludeList() != null && !dto.getTpIncludeExcludeList().isEmpty()) {

                List<String> tpIncludeIds = new ArrayList<>();
                List<String> tpExcludeIds = new ArrayList<>();

                for (String element : dto.getTpIncludeExcludeList()) {

                    String[] values = splitBySeparator(element, SEPARATOR_TILDE);

                    if (INCLUDE_FLAG.equals(values[1])) {
                        tpIncludeIds.add(values[0]);
                    } else {
                        tpExcludeIds.add(values[0]);
                    }
                }

                String tpIncludeList = tpIncludeIds.isEmpty() ? null : String.join(",", tpIncludeIds);
                String tpExcludeList = tpExcludeIds.isEmpty() ? null : String.join(",", tpExcludeIds);

                try {
                    repository.insertTpRule(dto.getNetworkId(), dto.getPlanId(), tpIncludeList, tpExcludeList);
                } catch (DuplicateKeyException dupEx) {
                    // WHEN DUP_VAL_ON_INDEX equivalent - the unique index on
                    // (NETWORK_ID, PLAN_ID, CRITERIA_TYPE) was already hit,
                    // fall back to UPDATE exactly like the procedure does.
                    repository.updateTpRule(dto.getNetworkId(), dto.getPlanId(), tpIncludeList, tpExcludeList);
                }
            } else {
                repository.deleteTpRule(dto.getNetworkId(), dto.getPlanId());
            }

            logger.info("MODIFY plan subscription rule completed successfully networkId={} planId={}",
                    dto.getNetworkId(), dto.getPlanId());

        } catch (Exception e) {
            errorCode = -1;
            errorDesc = buildErrorDesc(e);
            logger.error("MODIFY plan subscription rule failed networkId={} planId={} error={}",
                    dto.getNetworkId(), dto.getPlanId(), errorDesc, e);
        }

        return new PlanSubscriptionRuleResponseDto(errorCode, errorDesc);
    }

    @Transactional(readOnly = true)
    public PlanSubscriptionRuleViewResponseDto viewPlanSubscriptionRule(PlanSubscriptionRuleKeyDto key) {

        PlanSubscriptionRuleViewResponseDto response = new PlanSubscriptionRuleViewResponseDto();
        response.setAtpIncludeExcludeList(new ArrayList<>());
        response.setAtpToBeAddedList(new ArrayList<>());
        response.setAtpToBeRemovedList(new ArrayList<>());
        response.setTpIncludeExcludeList(new ArrayList<>());

        int errorCode = 0;
        String errorDesc = "SUCCESS";

        try {
            logger.info("VIEW plan subscription rule started networkId={} planId={}",
                    key.getNetworkId(), key.getPlanId());

           
            Optional<Map<String, Object>> atpRow =
                    repository.findAtpRuleWithPlanName(key.getPlanId(), key.getNetworkId());

            String includeList = null;
            String excludeList = null;
            String toBeAddedList = null;
            String toBeRemovedList = null;
            String includeAllAnyFlag = null;
            String excludeAllAnyFlag = null;

            if (atpRow.isPresent()) {
                Map<String, Object> row = atpRow.get();
                includeList = asString(row.get("INCLUDE_ATP_LIST"));
                excludeList = asString(row.get("EXCLUDE_ATP_LIST"));
                toBeAddedList = asString(row.get("TO_BE_ADDED_LIST"));
                toBeRemovedList = asString(row.get("TO_BE_REMOVED_LIST"));
                includeAllAnyFlag = asString(row.get("INCLUDE_LIST_RULE_TYPE"));
                excludeAllAnyFlag = asString(row.get("EXCLUDE_LIST_RULE_TYPE"));
                response.setPlanName(asString(row.get("SERVICE_PACKAGE_DESC")));
                response.setExtendAccountValidity(asString(row.get("EXTEND_ACCOUNT_VALIDITY")));
            }

          
            if (includeList != null) {
                for (String id : includeList.split(",", -1)) {
                    Map<String, Object> pkg = repository.findServicePackageNameAndId(id);
                    String name = asString(pkg.get("SERVICE_PACKAGE_DESC"));
                    String pkgId = asString(pkg.get("SERVICE_PACKAGE_ID"));
                    response.getAtpIncludeExcludeList().add(name + "~" + "I" + "#" + pkgId + "~" + includeAllAnyFlag);
                }
            }

           
            if (excludeList != null) {
                for (String id : excludeList.split(",", -1)) {
                    Map<String, Object> pkg = repository.findServicePackageNameAndId(id);
                    String name = asString(pkg.get("SERVICE_PACKAGE_DESC"));
                    String pkgId = asString(pkg.get("SERVICE_PACKAGE_ID"));
                    response.getAtpIncludeExcludeList().add(name + "~" + "E" + "#" + pkgId + "~" + excludeAllAnyFlag);
                }
            }

           
            if (toBeAddedList != null) {
                for (String id : toBeAddedList.split(",", -1)) {
                    Map<String, Object> pkg = repository.findServicePackageNameAndId(id);
                    String name = asString(pkg.get("SERVICE_PACKAGE_DESC"));
                    String pkgId = asString(pkg.get("SERVICE_PACKAGE_ID"));
                    response.getAtpToBeAddedList().add(name + "#" + pkgId);
                }
            }

            
            if (toBeRemovedList != null) {
                for (String id : toBeRemovedList.split(",", -1)) {
                    Map<String, Object> pkg = repository.findServicePackageNameAndId(id);
                    String name = asString(pkg.get("SERVICE_PACKAGE_DESC"));
                    String pkgId = asString(pkg.get("SERVICE_PACKAGE_ID"));
                    response.getAtpToBeRemovedList().add(name + "#" + pkgId);
                }
            }

            // -------------------------------------------------------------
            // TP row - NO_DATA_FOUND -> no values populated, not a failure.
            // -------------------------------------------------------------
            Optional<Map<String, Object>> tpRow = repository.findTpRule(key.getPlanId(), key.getNetworkId());

            String tpIncludeList = null;
            String tpExcludeList = null;

            if (tpRow.isPresent()) {
                tpIncludeList = asString(tpRow.get().get("INCLUDE_ATP_LIST"));
                tpExcludeList = asString(tpRow.get().get("EXCLUDE_ATP_LIST"));
            }

            // "name~I#id" (no ALL/ANY suffix for TP, matching the procedure)
            if (tpIncludeList != null) {
                for (String id : tpIncludeList.split(",", -1)) {
                    Map<String, Object> pack = repository.findTariffPackageNameAndId(id);
                    String name = asString(pack.get("TARIFF_PACKAGE_DESC"));
                    String packId = asString(pack.get("TARIFF_PACKAGE_ID"));
                    response.getTpIncludeExcludeList().add(name + "~" + "I" + "#" + packId);
                }
            }

            // "name~E#id"
            if (tpExcludeList != null) {
                for (String id : tpExcludeList.split(",", -1)) {
                    Map<String, Object> pack = repository.findTariffPackageNameAndId(id);
                    String name = asString(pack.get("TARIFF_PACKAGE_DESC"));
                    String packId = asString(pack.get("TARIFF_PACKAGE_ID"));
                    response.getTpIncludeExcludeList().add(name + "~" + "E" + "#" + packId);
                }
            }

            logger.info("VIEW plan subscription rule completed successfully networkId={} planId={}",
                    key.getNetworkId(), key.getPlanId());

        } catch (Exception e) {
            errorCode = -1;
            errorDesc = buildErrorDesc(e);
            logger.error("VIEW plan subscription rule failed networkId={} planId={} error={}",
                    key.getNetworkId(), key.getPlanId(), errorDesc, e);
        }

        response.setErrorCode(errorCode);
        response.setErrorDesc(errorDesc);
        return response;
    }

    @Transactional
    public PlanSubscriptionRuleResponseDto deletePlanSubscriptionRule(PlanSubscriptionRuleKeyDto key) {

        int errorCode = 0;
        String errorDesc = "SUCCESS";

        try {
            logger.info("DELETE plan subscription rule started networkId={} planId={}",
                    key.getNetworkId(), key.getPlanId());

            // DELETE FROM plan_subscription_rules WHERE plan_id = ? AND network_id = ?;
            repository.deleteAllRulesForPlan(key.getNetworkId(), key.getPlanId());

            logger.info("DELETE plan subscription rule completed successfully networkId={} planId={}",
                    key.getNetworkId(), key.getPlanId());

        } catch (Exception e) {
            errorCode = -1;
            errorDesc = buildErrorDesc(e);
            logger.error("DELETE plan subscription rule failed networkId={} planId={} error={}",
                    key.getNetworkId(), key.getPlanId(), errorDesc, e);
        }

        return new PlanSubscriptionRuleResponseDto(errorCode, errorDesc);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

  
    private String[] splitBySeparator(String value, String separator) {
        return value.split(separator, -1);
    }

   
    private String joinIfPresent(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }

    private String buildErrorDesc(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String fullTrace = sw.toString() + "---->" + e.getMessage();
        return fullTrace.length() > 1000 ? fullTrace.substring(0, 1000) : fullTrace;
    }
}