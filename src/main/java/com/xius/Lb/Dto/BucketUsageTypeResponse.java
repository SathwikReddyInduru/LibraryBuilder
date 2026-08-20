package com.xius.Lb.Dto;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;


@AllArgsConstructor
@NoArgsConstructor
public class BucketUsageTypeResponse {

    private Long usageBinaryId;
    private String usageType;
    private String balanceCategory;
    public Long getUsageBinaryId() {
        return usageBinaryId;
    }
    public void setUsageBinaryId(Long usageBinaryId) {
        this.usageBinaryId = usageBinaryId;
    }
    public String getUsageType() {
        return usageType;
    }
    public void setUsageType(String usageType) {
        this.usageType = usageType;
    }
    public String getBalanceCategory() {
        return balanceCategory;
    }
    public void setBalanceCategory(String balanceCategory) {
        this.balanceCategory = balanceCategory;
    }
}