package com.xius.TariffBuilder.Dto;

import java.util.List;


public class TariffPackageDetailsDto {

	private Long tariff_package_id;

	private String startDate;

	public Long getTariff_package_id() {
		return tariff_package_id;
	}
	public void setTariff_package_id(Long tariff_package_id) {
		this.tariff_package_id = tariff_package_id;
	}
	public String getStartDate() {
		return startDate;
	}
	public void setStartDate(String startDate) {
		this.startDate = startDate;
	}
	public String getTariffPackageDesc() {
		return tariffPackageDesc;
	}
	public void setTariffPackageDesc(String tariffPackageDesc) {
		this.tariffPackageDesc = tariffPackageDesc;
	}
	public Double getActivationFee() {
		return activationFee;
	}
	public void setActivationFee(Double activationFee) {
		this.activationFee = activationFee;
	}
	public String getRentalType() {
		return rentalType;
	}
	public void setRentalType(String rentalType) {
		this.rentalType = rentalType;
	}
	public Long getRentalPeriod() {
		return rentalPeriod;
	}
	public void setRentalPeriod(Long rentalPeriod) {
		this.rentalPeriod = rentalPeriod;
	}
	public String getDataBenefit() {
		return dataBenefit;
	}
	public void setDataBenefit(String dataBenefit) {
		this.dataBenefit = dataBenefit;
	}
	public String getSmsBenefit() {
		return smsBenefit;
	}
	public void setSmsBenefit(String smsBenefit) {
		this.smsBenefit = smsBenefit;
	}
	public String getVoiceBenefit() {
		return voiceBenefit;
	}
	public void setVoiceBenefit(String voiceBenefit) {
		this.voiceBenefit = voiceBenefit;
	}
	public String getPackageType() {
		return packageType;
	}
	public void setPackageType(String packageType) {
		this.packageType = packageType;
	}
	public List<String> getRateGroupNames() {
		return rateGroupNames;
	}
	public void setRateGroupNames(List<String> rateGroupNames) {
		this.rateGroupNames = rateGroupNames;
	}
	public List<DatpBenefitDto> getDatpBenefits() {
		return datpBenefits;
	}
	public void setDatpBenefits(List<DatpBenefitDto> datpBenefits) {
		this.datpBenefits = datpBenefits;
	}
	public String getIsCorporateYn() {
		return isCorporateYn;
	}
	public void setIsCorporateYn(String isCorporateYn) {
		this.isCorporateYn = isCorporateYn;
	}
	private String tariffPackageDesc;

	private Double activationFee;

	private String rentalType;

	private Long rentalPeriod;

	private String dataBenefit;

	private String smsBenefit;

	private String voiceBenefit;
	private String packageType;

	private List<String> rateGroupNames;
	private List<DatpBenefitDto> datpBenefits;
	private String isCorporateYn;
}