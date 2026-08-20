package com.xius.Lb.Dto;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class ZoneGroupResponse {

    private Long zoneGroupId;
    private String zoneGroupName;
    private String rating_yn;
    
    public Long getZoneGroupId() {
        return zoneGroupId;
    }
    public void setZoneGroupId(Long zoneGroupId) {
        this.zoneGroupId = zoneGroupId;
    }
    public String getZoneGroupName() {
        return zoneGroupName;
    }
    public void setZoneGroupName(String zoneGroupName) {
        this.zoneGroupName = zoneGroupName;
    }
    public String getRating_yn() {
        return rating_yn;
    }
    public void setRating_yn(String rating_yn) {
        this.rating_yn = rating_yn;
    }
}