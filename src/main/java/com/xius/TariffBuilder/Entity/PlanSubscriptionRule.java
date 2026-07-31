package com.xius.TariffBuilder.Entity;

/**
 * Plain POJO mapping to table PLAN_SUBSCRIPTION_RULES.
 * NOTE: This is NOT a JPA @Entity - no ORM annotations are used.
 * All persistence is done via JdbcTemplate with direct SQL in the Repository layer.
 */
public class PlanSubscriptionRule {

    private Long networkId;
    private Long planId;
    private String criteriaType;              // 'ATP' or 'TP'
    private String includeAtpList;             // comma separated ids
    private String excludeAtpList;             // comma separated ids
    private String toBeAddedList;              // comma separated ids
    private String toBeRemovedList;            // comma separated ids
    private String includeListRuleType;        // 'ALL' / 'ANY'
    private String excludeListRuleType;        // 'ALL' / 'ANY'
    private String extendAccountValidity;      // 'Y' / 'N' / 'C'

    public PlanSubscriptionRule() {
    }

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

    public String getCriteriaType() {
        return criteriaType;
    }

    public void setCriteriaType(String criteriaType) {
        this.criteriaType = criteriaType;
    }

    public String getIncludeAtpList() {
        return includeAtpList;
    }

    public void setIncludeAtpList(String includeAtpList) {
        this.includeAtpList = includeAtpList;
    }

    public String getExcludeAtpList() {
        return excludeAtpList;
    }

    public void setExcludeAtpList(String excludeAtpList) {
        this.excludeAtpList = excludeAtpList;
    }

    public String getToBeAddedList() {
        return toBeAddedList;
    }

    public void setToBeAddedList(String toBeAddedList) {
        this.toBeAddedList = toBeAddedList;
    }

    public String getToBeRemovedList() {
        return toBeRemovedList;
    }

    public void setToBeRemovedList(String toBeRemovedList) {
        this.toBeRemovedList = toBeRemovedList;
    }

    public String getIncludeListRuleType() {
        return includeListRuleType;
    }

    public void setIncludeListRuleType(String includeListRuleType) {
        this.includeListRuleType = includeListRuleType;
    }

    public String getExcludeListRuleType() {
        return excludeListRuleType;
    }

    public void setExcludeListRuleType(String excludeListRuleType) {
        this.excludeListRuleType = excludeListRuleType;
    }

    public String getExtendAccountValidity() {
        return extendAccountValidity;
    }

    public void setExtendAccountValidity(String extendAccountValidity) {
        this.extendAccountValidity = extendAccountValidity;
    }
}