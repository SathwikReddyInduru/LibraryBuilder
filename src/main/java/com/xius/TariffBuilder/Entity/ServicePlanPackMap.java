package com.xius.TariffBuilder.Entity;



public class ServicePlanPackMap {

	private String servicePackageId;


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

	public Integer getNetworkId() {
		return networkId;
	}

	public void setNetworkId(Integer networkId) {
		this.networkId = networkId;
	}

	public String getTariffPlanType() {
		return tariffPlanType;
	}

	public void setTariffPlanType(String tariffPlanType) {
		this.tariffPlanType = tariffPlanType;
	}

	public String getServiceTypes() {
		return serviceTypes;
	}

	public void setServiceTypes(String serviceTypes) {
		this.serviceTypes = serviceTypes;
	}

	public String getAtpCategory() {
		return atpCategory;
	}

	public void setAtpCategory(String atpCategory) {
		this.atpCategory = atpCategory;
	}

	private String servicePackageName;


	private Integer networkId;


	private String tariffPlanType;


	private String serviceTypes;

	private String atpCategory;
}