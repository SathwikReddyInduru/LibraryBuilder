package com.xius.Lb.Dto;

import java.util.List;

// import com.fasterxml.jackson.annotation.JsonPropertyOrder;

// @JsonPropertyOrder({
//     "balanceCategory",
//     "bucketType",
//     "usageType",
//     "bucketUnitType",
//     "bucketUnitValue",
//     "unlimitedUsageYn"
// })
public class BalanceCategoryRequest {

	private String bucketId;

    private String balanceCategory;

    private String bucketType;

    private List<Long> usageType;

    private String bucketUnitType;

    private Long bucketUnitValue;

    private String unlimitedUsageYn;

	public String getUnlimitedUsageYn() {
		return unlimitedUsageYn;
	}

	public void setUnlimitedUsageYn(String unlimitedUsageYn) {
		this.unlimitedUsageYn = unlimitedUsageYn;
	}

	public Long getBucketUnitValue() {
		return bucketUnitValue;
	}

	public void setBucketUnitValue(Long bucketUnitValue) {
		this.bucketUnitValue = bucketUnitValue;
	}

	public String getBucketUnitType() {
		return bucketUnitType;
	}

	public void setBucketUnitType(String bucketUnitType) {
		this.bucketUnitType = bucketUnitType;
	}

	public List<Long> getUsageType() {
		return usageType;
	}

	public void setUsageType(List<Long> usageType) {
		this.usageType = usageType;
	}

	public String getBucketType() {
		return bucketType;
	}

	public void setBucketType(String bucketType) {
		this.bucketType = bucketType;
	}

	public String getBalanceCategory() {
		return balanceCategory;
	}

	public void setBalanceCategory(String balanceCategory) {
		this.balanceCategory = balanceCategory;
	}

	public String getBucketId() {
		return bucketId;
	}

	public void setBucketId(String bucketId) {
		this.bucketId = bucketId;
	}
}