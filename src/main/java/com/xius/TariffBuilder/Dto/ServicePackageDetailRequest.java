package com.xius.TariffBuilder.Dto;

public class ServicePackageDetailRequest {
	
	    private Long networkId;
	    private Long servicePackageId;
	    private String monthYear;

	    public Long getNetworkId() {
	        return networkId;
	    }

	    public void setNetworkId(Long networkId) {
	        this.networkId = networkId;
	    }

	    public Long getServicePackageId() {
	        return servicePackageId;
	    }

	    public void setServicePackageId(Long servicePackageId) {
	        this.servicePackageId = servicePackageId;
	    }

	    public String getMonthYear() {
	        return monthYear;
	    }

	    public void setMonthYear(String monthYear) {
	        this.monthYear = monthYear;
	    }
}
