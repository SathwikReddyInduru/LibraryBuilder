package com.xius.TariffBuilder.Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xius.TariffBuilder.Dto.PlanSubscriptionRuleAddRequestDto;
import com.xius.TariffBuilder.Dto.PlanSubscriptionRuleKeyDto;
import com.xius.TariffBuilder.Dto.PlanSubscriptionRuleResponseDto;
import com.xius.TariffBuilder.Dto.PlanSubscriptionRuleViewResponseDto;
import com.xius.TariffBuilder.UserService.PlanSubscriptionRuleService;

/*
 * REST endpoints for Plan Subscription Rules (ATP business rules migration
 * from pro_plan_subscrib_rules_config). Exposes all 4 actions of the
 * procedure: CREATE (ADD), VIEW, MODIFY, DELETE.
 */
@RestController
@CrossOrigin(origins = "*")
public class PlanSubscriptionRuleController {

    private static final Logger logger = LoggerFactory.getLogger(PlanSubscriptionRuleController.class);

    private final PlanSubscriptionRuleService planSubscriptionRuleService;

    PlanSubscriptionRuleController(PlanSubscriptionRuleService planSubscriptionRuleService) {
        this.planSubscriptionRuleService = planSubscriptionRuleService;
    }

    @PostMapping("/planSubscriptionRules/add")
    public ResponseEntity<PlanSubscriptionRuleResponseDto> addPlanSubscriptionRule(
            @RequestBody PlanSubscriptionRuleAddRequestDto request) {

        logger.info("Add plan subscription rule request received networkId={} planId={}",
                request.getNetworkId(), request.getPlanId());

        PlanSubscriptionRuleResponseDto response = planSubscriptionRuleService.addPlanSubscriptionRule(request);

        logger.info("Add plan subscription rule request completed networkId={} planId={} errorCode={}",
                request.getNetworkId(), request.getPlanId(), response.getErrorCode());

        return ResponseEntity.ok(response);
    }

    @PutMapping("/planSubscriptionRules/modify")
    public ResponseEntity<PlanSubscriptionRuleResponseDto> modifyPlanSubscriptionRule(
            @RequestBody PlanSubscriptionRuleAddRequestDto request) {

        logger.info("Modify plan subscription rule request received networkId={} planId={}",
                request.getNetworkId(), request.getPlanId());

        PlanSubscriptionRuleResponseDto response = planSubscriptionRuleService.modifyPlanSubscriptionRule(request);

        logger.info("Modify plan subscription rule request completed networkId={} planId={} errorCode={}",
                request.getNetworkId(), request.getPlanId(), response.getErrorCode());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/planSubscriptionRules/view")
    public ResponseEntity<PlanSubscriptionRuleViewResponseDto> viewPlanSubscriptionRule(
            @RequestParam Long networkId,
            @RequestParam Long planId) {

        logger.info("View plan subscription rule request received networkId={} planId={}", networkId, planId);

        PlanSubscriptionRuleKeyDto key = new PlanSubscriptionRuleKeyDto();
        key.setNetworkId(networkId);
        key.setPlanId(planId);

        PlanSubscriptionRuleViewResponseDto response = planSubscriptionRuleService.viewPlanSubscriptionRule(key);

        logger.info("View plan subscription rule request completed networkId={} planId={} errorCode={}",
                networkId, planId, response.getErrorCode());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/planSubscriptionRules/delete")
    public ResponseEntity<PlanSubscriptionRuleResponseDto> deletePlanSubscriptionRule(
            @RequestParam Long networkId,
            @RequestParam Long planId) {

        logger.info("Delete plan subscription rule request received networkId={} planId={}", networkId, planId);

        PlanSubscriptionRuleKeyDto key = new PlanSubscriptionRuleKeyDto();
        key.setNetworkId(networkId);
        key.setPlanId(planId);

        PlanSubscriptionRuleResponseDto response = planSubscriptionRuleService.deletePlanSubscriptionRule(key);

        logger.info("Delete plan subscription rule request completed networkId={} planId={} errorCode={}",
                networkId, planId, response.getErrorCode());

        return ResponseEntity.ok(response);
    }
}