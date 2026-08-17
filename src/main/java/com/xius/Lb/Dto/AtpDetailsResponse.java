package com.xius.Lb.Dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Response shape for GET /atp/{atpId}.
 *
 * NOTE: the deployed ObjectMapper has alphabetical property sorting enabled
 * (MapperFeature.SORT_PROPERTIES_ALPHABETICALLY / Spring's
 * spring.jackson.mapper.sort-properties-alphabetically=true) somewhere
 * outside this module's source (no application.properties/.yml ships with
 * this codebase), which silently reorders the JSON output. @JsonPropertyOrder
 * below pins the field order explicitly so it always wins over that setting.
 *
 * Mirrors AtpRequest field-for-field so the GET response looks like the
 * original create payload.
 *
 * typeOfService is reconstructed from cs_rat_service_plans.SERVICE_PLAN_TYPE
 * ('R' -> 1 "Rating", 'B' -> 2 "Billing"), not the per-plan numeric
 * TYPE_OF_SERVICE column (which is a different, VOICE/SMS/DATA concept).
 *
 * validityPeriodType is reconstructed from the bucket's validity_period_days
 * (-1 sentinel -> "UNLIMITED", any other value -> "LIMITED").
 *
 * billingServiceType is NOT persisted by the current insert flow — this
 * endpoint only ever creates Rating (VOICE/SMS/DATA) service plans, never
 * Billing plans, so it will always come back null.
 */
@JsonPropertyOrder({
    "networkId",
    "createdBy",
    "atpName",
    "typeOfService",
    "billingServiceType",
    "categoryOfferCode",
    "vipPlanFlagYn",
    "ratingType",
    "description",
    "publicityId",
    "validFrom",
    "validTo",
    "validityPeriodType",
    "validityPeriodDays",
    "applicableFromHrs",
    "applicableToHrs",
    "rollOverYn",
    "extendValidityYn",
    "roamingNetworks",
    "allowNationalRoamingData",
    "allowInternationalRoamingData",
    "simImsiFlag",
    "simRangeDetails",
    "balanceCategories",
    "derivedServiceSelections",
    "calendarConfig",
    "zoneGroup",
    "dataZoneGroupId"
})
public class AtpDetailsResponse {

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

    private List<Long> roamingNetworks;

    private String allowNationalRoamingData;

    private String allowInternationalRoamingData;

    private String simImsiFlag;

    private List<String> simRangeDetails;

    private List<BalanceCategoryRequest> balanceCategories;

    private List<String> derivedServiceSelections;

    private Long calendarConfig;

    private List<ZoneGroupRequest> zoneGroup;

    private List<ZoneGroupRequest> dataZoneGroupId;

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

    public List<Long> getRoamingNetworks() {
        return roamingNetworks;
    }

    public void setRoamingNetworks(List<Long> roamingNetworks) {
        this.roamingNetworks = roamingNetworks;
    }

    public String getAllowNationalRoamingData() {
        return allowNationalRoamingData;
    }

    public void setAllowNationalRoamingData(String allowNationalRoamingData) {
        this.allowNationalRoamingData = allowNationalRoamingData;
    }

    public String getAllowInternationalRoamingData() {
        return allowInternationalRoamingData;
    }

    public void setAllowInternationalRoamingData(String allowInternationalRoamingData) {
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

    public void setSimRangeDetails(List<String> simRangeDetails) {
        this.simRangeDetails = simRangeDetails;
    }

    public List<BalanceCategoryRequest> getBalanceCategories() {
        return balanceCategories;
    }

    public void setBalanceCategories(List<BalanceCategoryRequest> balanceCategories) {
        this.balanceCategories = balanceCategories;
    }

    public List<String> getDerivedServiceSelections() {
        return derivedServiceSelections;
    }

    public void setDerivedServiceSelections(List<String> derivedServiceSelections) {
        this.derivedServiceSelections = derivedServiceSelections;
    }

    public Long getCalendarConfig() {
        return calendarConfig;
    }

    public void setCalendarConfig(Long calendarConfig) {
        this.calendarConfig = calendarConfig;
    }

    public List<ZoneGroupRequest> getZoneGroup() {
        return zoneGroup;
    }

    public void setZoneGroup(List<ZoneGroupRequest> zoneGroup) {
        this.zoneGroup = zoneGroup;
    }

    public List<ZoneGroupRequest> getDataZoneGroupId() {
        return dataZoneGroupId;
    }

    public void setDataZoneGroupId(List<ZoneGroupRequest> dataZoneGroupId) {
        this.dataZoneGroupId = dataZoneGroupId;
    }
}