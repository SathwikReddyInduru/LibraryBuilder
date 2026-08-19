package com.xius.Lb.Dto;

import java.util.List;

public class AtpRequest {

    private Long networkId;

    private String createdBy;

    private String atpName;

    private Integer typeOfService;

    private Integer billingServiceType;

    private String categoryOfferCode;

    private String vipPlanFlagYn;

    private String ratingType;

    private String description;

    private String publicityId;

    private String validFrom;

    private String validTo;

    private String validityPeriodType;

    private Integer validityPeriodDays;

    private Integer applicableFromHrs;

    private Integer applicableToHrs;

    private String rollOverYn;

    private String extendValidityYn;

    private List<String> roamingNetworks;

    private String allowNationalRoamingData;

    private String allowInternationalRoamingData;

    private String simImsiFlag;

    private List<String> simRangeDetails;

    private List<BalanceCategoryRequest> balanceCategories;

    private List<String> derivedServiceSelections;

    private String calendarConfig;

    private String allowMtc;
    private String allowMoc;
    private String allowNldMo;
    private String allowIldMo;

    private List<ZoneGroupRequest> zoneGroup;

    private List<ZoneGroupRequest> dataZoneGroupId;

  
    private Long tariffPlanId;


    // ============================================================
    // GETTERS / SETTERS
    // ============================================================

    public Long getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Long networkId) {
        this.networkId = networkId;
    }


    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }


    public String getAtpName() {
        return atpName;
    }

    public void setAtpName(String atpName) {
        this.atpName = atpName;
    }


    public Integer getTypeOfService() {
        return typeOfService;
    }

    public void setTypeOfService(Integer typeOfService) {
        this.typeOfService = typeOfService;
    }


    public Integer getBillingServiceType() {
        return billingServiceType;
    }

    public void setBillingServiceType(Integer billingServiceType) {
        this.billingServiceType = billingServiceType;
    }


    public String getCategoryOfferCode() {
        return categoryOfferCode;
    }

    public void setCategoryOfferCode(String categoryOfferCode) {
        this.categoryOfferCode = categoryOfferCode;
    }


    public String getVipPlanFlagYn() {
        return vipPlanFlagYn;
    }

    public void setVipPlanFlagYn(String vipPlanFlagYn) {
        this.vipPlanFlagYn = vipPlanFlagYn;
    }


    public String getRatingType() {
        return ratingType;
    }

    public void setRatingType(String ratingType) {
        this.ratingType = ratingType;
    }


    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    public String getPublicityId() {
        return publicityId;
    }

    public void setPublicityId(String publicityId) {
        this.publicityId = publicityId;
    }


    public String getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(String validFrom) {
        this.validFrom = validFrom;
    }


    public String getValidTo() {
        return validTo;
    }

    public void setValidTo(String validTo) {
        this.validTo = validTo;
    }


    public String getValidityPeriodType() {
        return validityPeriodType;
    }

    public void setValidityPeriodType(String validityPeriodType) {
        this.validityPeriodType = validityPeriodType;
    }


    public Integer getValidityPeriodDays() {
        return validityPeriodDays;
    }

    public void setValidityPeriodDays(Integer validityPeriodDays) {
        this.validityPeriodDays = validityPeriodDays;
    }


    public Integer getApplicableFromHrs() {
        return applicableFromHrs;
    }

    public void setApplicableFromHrs(Integer applicableFromHrs) {
        this.applicableFromHrs = applicableFromHrs;
    }


    public Integer getApplicableToHrs() {
        return applicableToHrs;
    }

    public void setApplicableToHrs(Integer applicableToHrs) {
        this.applicableToHrs = applicableToHrs;
    }


    public String getRollOverYn() {
        return rollOverYn;
    }

    public void setRollOverYn(String rollOverYn) {
        this.rollOverYn = rollOverYn;
    }


    public String getExtendValidityYn() {
        return extendValidityYn;
    }

    public void setExtendValidityYn(String extendValidityYn) {
        this.extendValidityYn = extendValidityYn;
    }


    public List<String> getRoamingNetworks() {
        return roamingNetworks;
    }

    public void setRoamingNetworks(List<String> roamingNetworks) {
        this.roamingNetworks = roamingNetworks;
    }


    public String getAllowNationalRoamingData() {
        return allowNationalRoamingData;
    }

    public void setAllowNationalRoamingData(
            String allowNationalRoamingData) {
        this.allowNationalRoamingData = allowNationalRoamingData;
    }


    public String getAllowInternationalRoamingData() {
        return allowInternationalRoamingData;
    }

    public void setAllowInternationalRoamingData(
            String allowInternationalRoamingData) {
        this.allowInternationalRoamingData = allowInternationalRoamingData;
    }


    public String getSimImsiFlag() {
        return simImsiFlag;
    }

    public void setSimImsiFlag(String simImsiFlag) {
        this.simImsiFlag = simImsiFlag;
    }


    public List<String> getSimRangeDetails() {
        return simRangeDetails;
    }

    public void setSimRangeDetails(
            List<String> simRangeDetails) {
        this.simRangeDetails = simRangeDetails;
    }


    public List<BalanceCategoryRequest> getBalanceCategories() {
        return balanceCategories;
    }

    public void setBalanceCategories(
            List<BalanceCategoryRequest> balanceCategories) {
        this.balanceCategories = balanceCategories;
    }


    public List<String> getDerivedServiceSelections() {
        return derivedServiceSelections;
    }

    public void setDerivedServiceSelections(
            List<String> derivedServiceSelections) {
        this.derivedServiceSelections = derivedServiceSelections;
    }


    public String getCalendarConfig() {
        return calendarConfig;
    }

    public void setCalendarConfig(String calendarConfig) {
        this.calendarConfig = calendarConfig;
    }


    public List<ZoneGroupRequest> getZoneGroup() {
        return zoneGroup;
    }

    public void setZoneGroup(
            List<ZoneGroupRequest> zoneGroup) {
        this.zoneGroup = zoneGroup;
    }


    public List<ZoneGroupRequest> getDataZoneGroupId() {
        return dataZoneGroupId;
    }

    public void setDataZoneGroupId(
            List<ZoneGroupRequest> dataZoneGroupId) {
        this.dataZoneGroupId = dataZoneGroupId;
    }

    public String getAllowMtc() {
        return allowMtc;
    }

    public void setAllowMtc(String allowMtc) {
        this.allowMtc = allowMtc;
    }

    public String getAllowMoc() {
        return allowMoc;
    }

    public void setAllowMoc(String allowMoc) {
        this.allowMoc = allowMoc;
    }

    public String getAllowNldMo() {
        return allowNldMo;
    }

    public void setAllowNldMo(String allowNldMo) {
        this.allowNldMo = allowNldMo;
    }

    public String getAllowIldMo() {
        return allowIldMo;
    }

    public void setAllowIldMo(String allowIldMo) {
        this.allowIldMo = allowIldMo;
    }

    public Long getTariffPlanId() {
        return tariffPlanId;
    }

    public void setTariffPlanId(Long tariffPlanId) {
        this.tariffPlanId = tariffPlanId;
    }

}