package com.xius.TariffBuilder.Dto;

import java.math.BigDecimal;

public class ServicePackageDetailDto {

    private String servicePackageDesc;
    private BigDecimal activationFee;

    public String getServicePackageDesc() {
        return servicePackageDesc;
    }

    public void setServicePackageDesc(String servicePackageDesc) {
        this.servicePackageDesc = servicePackageDesc;
    }

    public BigDecimal getActivationFee() {
        return activationFee;
    }

    public void setActivationFee(BigDecimal activationFee) {
        this.activationFee = activationFee;
    }
	
}