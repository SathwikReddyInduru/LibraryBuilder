package com.xius.Lb.Dto;

public class AtpListItem {

    private Long servicePackageId;
    private String servicePackageDesc;

    public AtpListItem() {
    }

    public AtpListItem(Long servicePackageId, String servicePackageDesc) {
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