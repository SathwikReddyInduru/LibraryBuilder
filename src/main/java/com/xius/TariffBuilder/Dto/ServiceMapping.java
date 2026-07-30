package com.xius.TariffBuilder.Dto;

public class ServiceMapping {
    private Long serviceId;
    private String serviceUnitType;
    private Integer units;
    private Integer topupCharge;
    private Integer maxTransferLimit;
    public Long getServiceId() {
        return serviceId;
    }
    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }
    public String getServiceUnitType() {
        return serviceUnitType;
    }
    public void setServiceUnitType(String serviceUnitType) {
        this.serviceUnitType = serviceUnitType;
    }
    public Integer getUnits() {
        return units;
    }
    public void setUnits(Integer units) {
        this.units = units;
    }
    public Integer getTopupCharge() {
        return topupCharge;
    }
    public void setTopupCharge(Integer topupCharge) {
        this.topupCharge = topupCharge;
    }
    public Integer getMaxTransferLimit() {
        return maxTransferLimit;
    }
    public void setMaxTransferLimit(Integer maxTransferLimit) {
        this.maxTransferLimit = maxTransferLimit;
    }
}

   
