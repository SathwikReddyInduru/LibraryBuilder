package com.xius.Lb.Dto;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class DerivedServiceResponse {

    private Long basicServiceId;
    private Long derivedServiceId;
    private String derivedSvcName;
    private String zoneGroupYn;
    public Long getBasicServiceId() {
        return basicServiceId;
    }
    public void setBasicServiceId(Long basicServiceId) {
        this.basicServiceId = basicServiceId;
    }
    public Long getDerivedServiceId() {
        return derivedServiceId;
    }
    public void setDerivedServiceId(Long derivedServiceId) {
        this.derivedServiceId = derivedServiceId;
    }
    public String getDerivedSvcName() {
        return derivedSvcName;
    }
    public void setDerivedSvcName(String derivedSvcName) {
        this.derivedSvcName = derivedSvcName;
    }
    public String getZoneGroupYn() {
        return zoneGroupYn;
    }
    public void setZoneGroupYn(String zoneGroupYn) {
        this.zoneGroupYn = zoneGroupYn;
    }
}