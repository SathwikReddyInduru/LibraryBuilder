package com.xius.TariffBuilder.Dto;

public class ServicePackageDto {

    private String servicePackageId;
    private String servicePackageName;
//    private Double activationFee;
//    private Long chargeId;
//    
    public ServicePackageDto() {
    }

    public String getServicePackageId() {
        return servicePackageId;
    }

    public void setServicePackageId(String servicePackageId) {
        this.servicePackageId = servicePackageId;
    }

    public String getServicePackageName() {
        return servicePackageName;
    }

    public void setServicePackageName(String servicePackageName) {
        this.servicePackageName = servicePackageName;
    }
    
//	public Double getActivationFee() {
//		return activationFee;
//	}
//	public void setActivationFee(Double activationFee) {
//		this.activationFee = activationFee;
//	}
//	public Long getChargeId() {
//		return chargeId;
//	}
//	public void setChargeId(Long chargeId) {
//		this.chargeId = chargeId;
//	}
}