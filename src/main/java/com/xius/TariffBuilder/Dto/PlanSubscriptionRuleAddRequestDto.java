package com.xius.TariffBuilder.Dto;

import java.util.List;

/**
 * Request DTO for CREATE (ADD) of a Plan Subscription Rule.
 *
 * Mirrors the IN parameters of pro_plan_subscrib_rules_config for pi_action_flag = 'ADD'.
 *
 * Element formats (same as Oracle arr_varchar elements, tilde separated):
 *   atpIncludeExcludeList element -> "<atpId>~<I|E>~<ALL|ANY>"
 *   tpIncludeExcludeList  element -> "<tpId>~<I|E>"
 *   atpToBeAddedList / atpToBeRemovedList elements -> plain "<atpId>"
 */
public class PlanSubscriptionRuleAddRequestDto {

    private Long networkId;
    private Long planId;

    private List<String> atpIncludeExcludeList;
    private List<String> atpToBeAddedList;
    private List<String> atpToBeRemovedList;
    private List<String> tpIncludeExcludeList;

    private String extendAccountValidity;

    public Long getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Long networkId) {
        this.networkId = networkId;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public List<String> getAtpIncludeExcludeList() {
        return atpIncludeExcludeList;
    }

    public void setAtpIncludeExcludeList(List<String> atpIncludeExcludeList) {
        this.atpIncludeExcludeList = atpIncludeExcludeList;
    }

    public List<String> getAtpToBeAddedList() {
        return atpToBeAddedList;
    }

    public void setAtpToBeAddedList(List<String> atpToBeAddedList) {
        this.atpToBeAddedList = atpToBeAddedList;
    }

    public List<String> getAtpToBeRemovedList() {
        return atpToBeRemovedList;
    }

    public void setAtpToBeRemovedList(List<String> atpToBeRemovedList) {
        this.atpToBeRemovedList = atpToBeRemovedList;
    }

    public List<String> getTpIncludeExcludeList() {
        return tpIncludeExcludeList;
    }

    public void setTpIncludeExcludeList(List<String> tpIncludeExcludeList) {
        this.tpIncludeExcludeList = tpIncludeExcludeList;
    }

    public String getExtendAccountValidity() {
        return extendAccountValidity;
    }

    public void setExtendAccountValidity(String extendAccountValidity) {
        this.extendAccountValidity = extendAccountValidity;
    }
}