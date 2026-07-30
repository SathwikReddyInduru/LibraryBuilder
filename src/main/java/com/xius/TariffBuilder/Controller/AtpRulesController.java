package com.xius.TariffBuilder.Controller;


import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xius.TariffBuilder.Dto.AtpRulesDto;
import com.xius.TariffBuilder.UserService.AtpRulesService;

import jakarta.servlet.http.HttpSession;

@RestController
@CrossOrigin(origins = "*")
public class AtpRulesController {

    private static final Logger logger = LoggerFactory.getLogger(AtpRulesController.class);

    private final AtpRulesService atpRulesService ;
    // private final PlanSubscriptionRulesService planSubscriptionRulesService;

    AtpRulesController(AtpRulesService atpRulesService) {
        this.atpRulesService = atpRulesService;
    }

    @GetMapping("/builder/added-packages")
    public List<AtpRulesDto> getAddOnPackages(
            @RequestParam(required = false) Long networkId,
            HttpSession session) {

        if (networkId == null) {
            networkId = (Long) session.getAttribute("networkId");
        }

        logger.info("Fetching add-on packages networkId={}", networkId);

        return atpRulesService.getAddOnPackages(networkId);
    }
    
    
    
    @GetMapping("/service-package-plan-mapping")
    public ResponseEntity<List<Map<String, Object>>> getServicePackagePlanMapping(
            @RequestParam Long networkId) {

        List<Map<String, Object>> response = atpRulesService.getServicePackagePlanMapping(networkId);

        return ResponseEntity.ok(response);
    }
}