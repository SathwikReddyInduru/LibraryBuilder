package com.xius.TariffBuilder.Dto;


import java.util.Date;

public class TariffPackageDetails {

    /*
     * CS_RAT_TARIFF_PACKAGE
     */
    private Long tariffPackageId;
    private String tariffPackageDesc;
    private String publicityId;
    private String chargeId;
    public Long getTariffPackageId() {
        return tariffPackageId;
    }

    public void setTariffPackageId(Long tariffPackageId) {
        this.tariffPackageId = tariffPackageId;
    }

    public String getTariffPackageDesc() {
        return tariffPackageDesc;
    }

    public void setTariffPackageDesc(String tariffPackageDesc) {
        this.tariffPackageDesc = tariffPackageDesc;
    }

    public String getPublicityId() {
        return publicityId;
    }

    public void setPublicityId(String publicityId) {
        this.publicityId = publicityId;
    }

    public String getChargeId() {
        return chargeId;
    }

    public void setChargeId(String chargeId) {
        this.chargeId = chargeId;
    }

    public String getDiscountOnRentalYn() {
        return discountOnRentalYn;
    }

    public void setDiscountOnRentalYn(String discountOnRentalYn) {
        this.discountOnRentalYn = discountOnRentalYn;
    }

    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
    }

    public String getIsCorporateYn() {
        return isCorporateYn;
    }

    public void setIsCorporateYn(String isCorporateYn) {
        this.isCorporateYn = isCorporateYn;
    }

    public String getTariffPackCategory() {
        return tariffPackCategory;
    }

    public void setTariffPackCategory(String tariffPackCategory) {
        this.tariffPackCategory = tariffPackCategory;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

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

    public String getTariffPlanType() {
        return tariffPlanType;
    }

    public void setTariffPlanType(String tariffPlanType) {
        this.tariffPlanType = tariffPlanType;
    }

    public String getServicePackageDesc() {
        return servicePackageDesc;
    }

    public void setServicePackageDesc(String servicePackageDesc) {
        this.servicePackageDesc = servicePackageDesc;
    }

    public String getSpChargeId() {
        return spChargeId;
    }

    public void setSpChargeId(String spChargeId) {
        this.spChargeId = spChargeId;
    }

    public Double getSpActivationFee() {
        return spActivationFee;
    }

    public void setSpActivationFee(Double spActivationFee) {
        this.spActivationFee = spActivationFee;
    }

    public Long getServicePlanId() {
        return servicePlanId;
    }

    public void setServicePlanId(Long servicePlanId) {
        this.servicePlanId = servicePlanId;
    }

    public String getServiceTypes() {
        return serviceTypes;
    }

    public void setServiceTypes(String serviceTypes) {
        this.serviceTypes = serviceTypes;
    }

    public String getChargeDesc() {
        return chargeDesc;
    }

    public void setChargeDesc(String chargeDesc) {
        this.chargeDesc = chargeDesc;
    }

    public String getRentalType() {
        return rentalType;
    }

    public void setRentalType(String rentalType) {
        this.rentalType = rentalType;
    }

    public Integer getRentalPeriod() {
        return rentalPeriod;
    }

    public void setRentalPeriod(Integer rentalPeriod) {
        this.rentalPeriod = rentalPeriod;
    }

    public Double getActivationFee() {
        return activationFee;
    }

    public void setActivationFee(Double activationFee) {
        this.activationFee = activationFee;
    }

    public Double getRentalFee() {
        return rentalFee;
    }

    public void setRentalFee(Double rentalFee) {
        this.rentalFee = rentalFee;
    }

    public Integer getRentalFreeCycles() {
        return rentalFreeCycles;
    }

    public void setRentalFreeCycles(Integer rentalFreeCycles) {
        this.rentalFreeCycles = rentalFreeCycles;
    }

    public String getAutoRenewal() {
        return autoRenewal;
    }

    public void setAutoRenewal(String autoRenewal) {
        this.autoRenewal = autoRenewal;
    }

    public String getPlanExpMidnightYn() {
        return planExpMidnightYn;
    }

    public void setPlanExpMidnightYn(String planExpMidnightYn) {
        this.planExpMidnightYn = planExpMidnightYn;
    }

    public Integer getMaxRenewalCount() {
        return maxRenewalCount;
    }

    public void setMaxRenewalCount(Integer maxRenewalCount) {
        this.maxRenewalCount = maxRenewalCount;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Double getMrp() {
        return mrp;
    }

    public void setMrp(Double mrp) {
        this.mrp = mrp;
    }

    private String discountOnRentalYn;
    private String packageType;
    private String isCorporateYn;
    private String tariffPackCategory;
    private Date startDate;
    private Date endDate;
    private Long networkId;

    /*
     * CS_RAT_TARIFF_SERVICE_PACK_MAP
     */
    private Long servicePackageId;
    private String tariffPlanType;

    /*
     * CS_RAT_SERVICE_PACKAGE
     */
    private String servicePackageDesc;
    private String spChargeId;
    private Double spActivationFee;

    /*
     * CS_RAT_SERVICE_PLAN_PACKAGE
     */
    private Long servicePlanId;

    /*
     * SERVICE TYPES
     */
    private String serviceTypes;

    /*
     * CS_RAT_PERIODIC_CHARGE_INFO
     */
    private String chargeDesc;
    private String rentalType;
    private Integer rentalPeriod;
    private Double activationFee;
    private Double rentalFee;
    private Integer rentalFreeCycles;
    private String autoRenewal;
    private String planExpMidnightYn;
    private Integer maxRenewalCount;
    private String createdBy;
    private Integer priority;

    /*
     * CS_RECHARGE_PRODUCTS (via CS_RC_PRODUCT_ATP_MAP) — MRP for RCATP rows only
     */
    private Double mrp;
}