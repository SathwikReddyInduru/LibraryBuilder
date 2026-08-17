package com.xius.Lb.Dto;

import java.util.List;

public class AtpResponse {

	    private Long atpId;
	    private Long bundleId;
	    private List<String> bucketIds;
	    private List<Long> servicePlanIds;
	    private Long tariffPlanId;
	    private String message;


	    public AtpResponse(
	            Long atpId,
	            Long bundleId,
	            List<String> bucketIds,
	            List<Long> servicePlanIds,
	            Long tariffPlanId,
	            String message) {

	        this.atpId = atpId;
	        this.bundleId = bundleId;
	        this.bucketIds = bucketIds;
	        this.servicePlanIds = servicePlanIds;
	        this.tariffPlanId = tariffPlanId;
	        this.message = message;
	    }

	    public Long getAtpId() {
	        return atpId;
	    }

	    public void setAtpId(Long atpId) {
	        this.atpId = atpId;
	    }

	    public Long getBundleId() {
	        return bundleId;
	    }

	    public void setBundleId(Long bundleId) {
	        this.bundleId = bundleId;
	    }

	    public List<String> getBucketIds() {
	        return bucketIds;
	    }

	    public void setBucketIds(List<String> bucketIds) {
	        this.bucketIds = bucketIds;
	    }

	    public List<Long> getServicePlanIds() {
	        return servicePlanIds;
	    }

	    public void setServicePlanIds(List<Long> servicePlanIds) {
	        this.servicePlanIds = servicePlanIds;
	    }

	    public Long getTariffPlanId() {
	        return tariffPlanId;
	    }

	    public void setTariffPlanId(Long tariffPlanId) {
	        this.tariffPlanId = tariffPlanId;
	    }

	    public String getMessage() {
	        return message;
	    }

	    public void setMessage(String message) {
	        this.message = message;
	    }
	}
