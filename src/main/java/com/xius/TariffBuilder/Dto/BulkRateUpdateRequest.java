package com.xius.TariffBuilder.Dto;


import java.math.BigDecimal;
import java.util.List;

public class BulkRateUpdateRequest {

    private Long networkId;
    private String userId;
    private List<Long> serviceIds;
    private List<BigDecimal> newRates;
    private String monthYear;
    private String flag;

    public Long getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Long networkId) {
        this.networkId = networkId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<Long> getServiceIds() {
        return serviceIds;
    }

    public void setServiceIds(List<Long> serviceIds) {
        this.serviceIds = serviceIds;
    }

    public List<BigDecimal> getNewRates() {
        return newRates;
    }

    public void setNewRates(List<BigDecimal> newRates) {
        this.newRates = newRates;
    }

    public String getMonthYear() {
        return monthYear;
    }

    public void setMonthYear(String monthYear) {
        this.monthYear = monthYear;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }
}