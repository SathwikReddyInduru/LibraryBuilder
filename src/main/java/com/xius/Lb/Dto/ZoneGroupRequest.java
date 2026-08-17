package com.xius.Lb.Dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
    "id",
    "ratingFlag"
})
public class ZoneGroupRequest {

    private Long id;

    private String ratingFlag;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public String getRatingFlag() {
        return ratingFlag;
    }

    public void setRatingFlag(String ratingFlag) {
        this.ratingFlag = ratingFlag;
    }
}