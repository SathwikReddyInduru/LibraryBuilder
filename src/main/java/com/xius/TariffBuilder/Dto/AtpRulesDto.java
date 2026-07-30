package com.xius.TariffBuilder.Dto;


public class AtpRulesDto {

    private Long servicePackageId;
    private String servicePackageDesc;

    public AtpRulesDto() {
    }

    public AtpRulesDto(Long servicePackageId, String servicePackageDesc) {
        this.servicePackageId = servicePackageId;
        this.servicePackageDesc = servicePackageDesc;
    }

    public Long getServicePackageId() {
        return servicePackageId;
    }

    public void setServicePackageId(Long servicePackageId) {
        this.servicePackageId = servicePackageId;
    }

    public String getServicePackageDesc() {
        return servicePackageDesc;
    }

    public void setServicePackageDesc(String servicePackageDesc) {
        this.servicePackageDesc = servicePackageDesc;
    }
}
