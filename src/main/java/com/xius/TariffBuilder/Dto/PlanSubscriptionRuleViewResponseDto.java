package com.xius.TariffBuilder.Dto;

import java.util.List;

/**
 * Response DTO for VIEW, mirroring the OUT parameters of
 * pro_plan_subscrib_rules_config for pi_action_flag = 'VIEW':
 *   po_plan_name, po_atp_include_exclude_list, po_atp_to_be_added_list,
 *   po_atp_to_be_removed_list, po_tp_include_exclude_list,
 *   po_extend_account_validity, po_error_code, po_error_desc.
 *
 * Element formats (same as the procedure builds them):
 *   atpIncludeExcludeList element -> "<name>~<I|E>#<id>~<ALL|ANY>"
 *   atpToBeAddedList / atpToBeRemovedList element -> "<name>#<id>"
 *   tpIncludeExcludeList element -> "<name>~<I|E>#<id>"
 */
public class PlanSubscriptionRuleViewResponseDto {

    private String planName;
    private List<String> atpIncludeExcludeList;
    private List<String> atpToBeAddedList;
    private List<String> atpToBeRemovedList;
    private List<String> tpIncludeExcludeList;
    private String extendAccountValidity;

    private int errorCode;
    private String errorDesc;

    public PlanSubscriptionRuleViewResponseDto() {
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public List<String> getAtpIncludeExcludeList() {
        return atpIncludeExcludeList;
    }

    public void setAtpIncludeExcludeList(List<String> atpIncludeExcludeList) {
        this.atpIncludeExcludeList = atpIncludeExcludeList;
    }

    public List<String> getAtpToBeAddedList() {
        return atpToBeAddedList;
    }

    public void setAtpToBeAddedList(List<String> atpToBeAddedList) {
        this.atpToBeAddedList = atpToBeAddedList;
    }

    public List<String> getAtpToBeRemovedList() {
        return atpToBeRemovedList;
    }

    public void setAtpToBeRemovedList(List<String> atpToBeRemovedList) {
        this.atpToBeRemovedList = atpToBeRemovedList;
    }

    public List<String> getTpIncludeExcludeList() {
        return tpIncludeExcludeList;
    }

    public void setTpIncludeExcludeList(List<String> tpIncludeExcludeList) {
        this.tpIncludeExcludeList = tpIncludeExcludeList;
    }

    public String getExtendAccountValidity() {
        return extendAccountValidity;
    }

    public void setExtendAccountValidity(String extendAccountValidity) {
        this.extendAccountValidity = extendAccountValidity;
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