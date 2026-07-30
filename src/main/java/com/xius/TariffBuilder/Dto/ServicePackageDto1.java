package com.xius.TariffBuilder.Dto;

public class ServicePackageDto1 {
	private String servicePackageId;
    private String servicePackageName;
    private Double activationFee;
    private String  chargeId;
    
    

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
    
	public Double getActivationFee() {
		return activationFee;
	}
	public void setActivationFee(Double activationFee) {
		this.activationFee = activationFee;
	}
	public String  getChargeId() {
		return chargeId;
	}
	public void setChargeId(String  chargeId) {
		this.chargeId = chargeId;
	}
}
