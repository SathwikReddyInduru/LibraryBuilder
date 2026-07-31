package com.xius.TariffBuilder.Dto;

/**
 * Response DTO mirroring po_error_code / po_error_desc OUT parameters of
 * pro_plan_subscrib_rules_config.
 *
 * errorCode = 0 and errorDesc = "SUCCESS" on success (same defaults the
 * procedure sets at the very start of the BEGIN block).
 * On any exception, errorCode / errorDesc are overwritten, exactly like the
 * WHEN OTHERS handler in the procedure.
 */
public class PlanSubscriptionRuleResponseDto {

    private int errorCode;
    private String errorDesc;

    public PlanSubscriptionRuleResponseDto() {
    }

    public PlanSubscriptionRuleResponseDto(int errorCode, String errorDesc) {
        this.errorCode = errorCode;
        this.errorDesc = errorDesc;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorDesc() {
        return errorDesc;
    }

    public void setErrorDesc(String errorDesc) {
        this.errorDesc = errorDesc;
    }
}