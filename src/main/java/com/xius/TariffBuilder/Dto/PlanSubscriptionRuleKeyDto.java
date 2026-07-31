package com.xius.TariffBuilder.Dto;

/**
 * Simple key DTO used for VIEW and DELETE requests, both of which only need
 * networkId + planId as input (pi_network_id / pi_plan_id in the procedure).
 */
public class PlanSubscriptionRuleKeyDto {

    private Long networkId;
    private Long planId;

    public PlanSubscriptionRuleKeyDto() {
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
}