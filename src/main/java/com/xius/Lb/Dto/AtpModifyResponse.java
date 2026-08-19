package com.xius.Lb.Dto;

import java.util.List;

/**
 * Response for PUT /atp/{atpId}. Reports exactly what Modify ATP changed so
 * the frontend can reconcile its local state without doing another GET.
 *
 * "Removed" here always means mapping-only removal - the underlying
 * bucket/service-plan/derived-service row is never deleted, only its link
 * to this ATP/bundle/tariff-plan.
 */
public class AtpModifyResponse {

	private Long atpId;
	private Long bundleId;
	private Long tariffPlanId;

	private List<String> addedBucketIds;
	private List<String> updatedBucketIds;
	private List<String> removedBucketIds;

	private List<Long> addedServicePlanIds;
	private List<Long> updatedServicePlanIds;
	private List<Long> removedServicePlanIds;

	private List<String> addedDerivedServiceSelections;
	private List<String> removedDerivedServiceSelections;

	private String message;

	public AtpModifyResponse() {
	}

	public AtpModifyResponse(Long atpId, Long bundleId, Long tariffPlanId, List<String> addedBucketIds,
			List<String> updatedBucketIds, List<String> removedBucketIds, List<Long> addedServicePlanIds,
			List<Long> updatedServicePlanIds, List<Long> removedServicePlanIds,
			List<String> addedDerivedServiceSelections, List<String> removedDerivedServiceSelections,
			String message) {

		this.atpId = atpId;
		this.bundleId = bundleId;
		this.tariffPlanId = tariffPlanId;
		this.addedBucketIds = addedBucketIds;
		this.updatedBucketIds = updatedBucketIds;
		this.removedBucketIds = removedBucketIds;
		this.addedServicePlanIds = addedServicePlanIds;
		this.updatedServicePlanIds = updatedServicePlanIds;
		this.removedServicePlanIds = removedServicePlanIds;
		this.addedDerivedServiceSelections = addedDerivedServiceSelections;
		this.removedDerivedServiceSelections = removedDerivedServiceSelections;
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

	public Long getTariffPlanId() {
		return tariffPlanId;
	}

	public void setTariffPlanId(Long tariffPlanId) {
		this.tariffPlanId = tariffPlanId;
	}

	public List<String> getAddedBucketIds() {
		return addedBucketIds;
	}

	public void setAddedBucketIds(List<String> addedBucketIds) {
		this.addedBucketIds = addedBucketIds;
	}

	public List<String> getUpdatedBucketIds() {
		return updatedBucketIds;
	}

	public void setUpdatedBucketIds(List<String> updatedBucketIds) {
		this.updatedBucketIds = updatedBucketIds;
	}

	public List<String> getRemovedBucketIds() {
		return removedBucketIds;
	}

	public void setRemovedBucketIds(List<String> removedBucketIds) {
		this.removedBucketIds = removedBucketIds;
	}

	public List<Long> getAddedServicePlanIds() {
		return addedServicePlanIds;
	}

	public void setAddedServicePlanIds(List<Long> addedServicePlanIds) {
		this.addedServicePlanIds = addedServicePlanIds;
	}

	public List<Long> getUpdatedServicePlanIds() {
		return updatedServicePlanIds;
	}

	public void setUpdatedServicePlanIds(List<Long> updatedServicePlanIds) {
		this.updatedServicePlanIds = updatedServicePlanIds;
	}

	public List<Long> getRemovedServicePlanIds() {
		return removedServicePlanIds;
	}

	public void setRemovedServicePlanIds(List<Long> removedServicePlanIds) {
		this.removedServicePlanIds = removedServicePlanIds;
	}

	public List<String> getAddedDerivedServiceSelections() {
		return addedDerivedServiceSelections;
	}

	public void setAddedDerivedServiceSelections(List<String> addedDerivedServiceSelections) {
		this.addedDerivedServiceSelections = addedDerivedServiceSelections;
	}

	public List<String> getRemovedDerivedServiceSelections() {
		return removedDerivedServiceSelections;
	}

	public void setRemovedDerivedServiceSelections(List<String> removedDerivedServiceSelections) {
		this.removedDerivedServiceSelections = removedDerivedServiceSelections;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
