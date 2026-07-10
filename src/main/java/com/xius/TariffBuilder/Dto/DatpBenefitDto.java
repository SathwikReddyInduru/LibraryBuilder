package com.xius.TariffBuilder.Dto;


import lombok.Data;

@Data
public class DatpBenefitDto {

    private Long datpId;
    private String datpName;

    private String voiceBenefit;
    private String smsBenefit;
    private String dataBenefit;
}
