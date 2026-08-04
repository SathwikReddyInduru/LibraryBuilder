package com.xius.TariffBuilder.Controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xius.TariffBuilder.Dto.TariffPackageDetailsDto;
import com.xius.TariffBuilder.Entity.ServicePlanPackMap;
import com.xius.TariffBuilder.UserService.SaveConfigService;
import com.xius.TariffBuilder.UserService.ServicePackageService;
import com.xius.TariffBuilder.UserService.ServicePlanService;
import com.xius.TariffBuilder.UserService.TariffApprovalService;
import com.xius.TariffBuilder.UserService.TariffPackageService;
import com.xius.TariffBuilder.UserService.TariffPackageSyncService;
import com.xius.TariffBuilder.UserService.TariffService;
import com.xius.TariffBuilder.UserService.TariffUpdateService;
import com.xius.TariffBuilder.util.JsonStorage;

import jakarta.servlet.http.HttpSession;

/*
 * Holds only the JSON/REST endpoints (previously mixed into BuilderController
 * alongside page-returning endpoints). @RestController implies @ResponseBody
 * on every handler, so the per-method @ResponseBody annotations from the
 * original controller are no longer needed here.
 */
@RestController
@CrossOrigin(origins = "*")
public class BuilderApiController {

    private static final Logger logger = LoggerFactory.getLogger(BuilderApiController.class);

    private final ServicePlanService service;

    private final TariffService tariffService;

    private final SaveConfigService saveConfigService;

    private final ServicePackageService servicePackageService;

    private final TariffApprovalService tariffApprovalService;

    private final JsonStorage jsonStorage;

    private final TariffPackageService tariffPackageService;

    private final TariffUpdateService tariffUpdateService;

    private final TariffPackageSyncService tariffPackageSyncService;

    BuilderApiController(ServicePlanService service, TariffService tariffService, SaveConfigService saveConfigService,
            ServicePackageService servicePackageService, TariffApprovalService tariffApprovalService,
            JsonStorage jsonStorage, TariffPackageService tariffPackageService,
            TariffUpdateService tariffUpdateService, TariffPackageSyncService tariffPackageSyncService) {
        this.service = service;
        this.tariffService = tariffService;
        this.saveConfigService = saveConfigService;
        this.servicePackageService = servicePackageService;
        this.tariffApprovalService = tariffApprovalService;
        this.jsonStorage = jsonStorage;
        this.tariffPackageService = tariffPackageService;
        this.tariffUpdateService = tariffUpdateService;
        this.tariffPackageSyncService = tariffPackageSyncService;
    }

    // ================= STEP FILTERS =================

    // @GetMapping("/builder/step2/filter")
    // public List<ServicePlanPackMap> getTpPlans(@RequestParam String types, HttpSession session) {

    //     Long networkId = (Long) session.getAttribute("networkId");

    //     logger.info("Fetching TP plans networkId={} types={}", networkId, types);

    //     return service.getPlans(networkId, types);
    // }

    @GetMapping("/builder/step2/filter")
public List<ServicePlanPackMap> getTpPlans(
        @RequestParam(required = false) Long networkId,
        @RequestParam String types,
        HttpSession session) {

    if (networkId == null) {
        networkId = (Long) session.getAttribute("networkId");
    }

     logger.info("Fetching TP plans networkId={} types={}", networkId, types);
    return service.getPlans(networkId, types);
}

    

    @GetMapping("/builder/step3/filter")
    public List<ServicePlanPackMap> getDAtpPlans(@RequestParam String types, HttpSession session) {

        Long networkId = (Long) session.getAttribute("networkId");

        logger.info("Fetching DATP plans networkId={} types={}", networkId, types);

        return service.getDAtpPlans(networkId, types);
    }


    // @GetMapping("/builder/step3/cafilter")
    // public String getCaAtps(@RequestParam String types, HttpSession session) {
    //     Long networkId = (Long) session.getAttribute("networkId");
    //     logger.info("Fetching DATP plans networkId={} types={}", networkId, types);
    //      return service.getCaAtps(networkId, types);
    // }

     @GetMapping("/builder/step5/cafilter")
public List<ServicePlanPackMap> getCaAtps(
        @RequestParam(required = false) Long networkId,
        @RequestParam String types,
        HttpSession session) {

    if (networkId == null) {
        networkId = (Long) session.getAttribute("networkId");
    }

     logger.info("Fetching TP plans networkId={} types={}", networkId, types);
    return service.getCaAtps(networkId, types);
}
    
    @GetMapping("/builder/step4/filter")
    public List<ServicePlanPackMap> getAAtpPlans(@RequestParam String types, HttpSession session) {

        Long networkId = (Long) session.getAttribute("networkId");

        logger.info("Fetching AATP plans networkId={} types={}", networkId, types);

        return service.getAAtpPlans(networkId, types);
    }

    // ================= HIERARCHY =================

    @GetMapping("/admin/hierarchy/{tpName}")
    public Object getHierarchy(@PathVariable String tpName) {

        // logger.info("Fetching hierarchy for tpName={}", tpName);

        return tariffService.getHierarchy(tpName);
    }

    // ================= SAVE CONFIG =================

    @PostMapping("/prepareSaveConfig")
    public ResponseEntity<?> prepareSaveConfig(@RequestBody Map<String, Object> request, HttpSession session) {

        String username = (String) session.getAttribute("username");

        Long networkId = (Long) session.getAttribute("networkId");

        logger.info("Prepare config called username={} networkId={}", username, networkId);

        Map<String, Object> response = saveConfigService.prepareConfig(request, username, networkId);

        logger.info("Prepare config completed");

        return ResponseEntity.ok(response);
    }

    // ================= CLONE =================

    /*
     * POST /clone/validate
     * Validates tpName and publicityId before a modify-mode clone.
     */
    // @PostMapping("/clone/validate")
    // public ResponseEntity<Map<String, Object>> validateClone(
    //         @RequestBody Map<String, Object> requestBody) {

    //     Long networkId = Long.valueOf(requestBody.get("networkId").toString());
    //     String tpName = requestBody.get("tpName").toString();
    //     String publicityId = requestBody.get("publicityId").toString();

    //     logger.info("Clone validate request networkId={} tpName={} publicityId={}", networkId, tpName, publicityId);

    //     Map<String, Object> result = tariffApprovalService.validateClone(networkId, tpName, publicityId);

    //     return ResponseEntity.ok(result);
    // }

    /*
     * POST /clone
     * Supports cloneMode=direct (add _CLn suffix) and cloneMode=modify
     * (use overrideTpName / overridePublicityId supplied by the frontend).
     * Always returns HTTP 200 with { status: "success"|"error", ... }.
     */
    @PostMapping("/clone")
    public ResponseEntity<Map<String, Object>> clone(@RequestBody Map<String, Object> requestBody) {

        String tpName = requestBody.get("tpName").toString();

        logger.info("Clone request received tpName={}", tpName);

        Map<String, Object> result = tariffApprovalService.clone(requestBody);

        logger.info("Clone completed tpName={} status={}", tpName, result.get("status"));

        return ResponseEntity.ok(result);
    }

    // ================= APPROVE / REJECT =================

    @PostMapping("/approve/{tpName}")
    public ResponseEntity<Map<String, Object>> approve(
            @PathVariable String tpName) {

        Map<String, Object> result = tariffApprovalService.approve(tpName);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/reject/{tpName}")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable String tpName,
            @RequestBody(required = false) Map<String, Object> body) {

        String remarks = (body != null && body.get("remarks") != null)
                ? body.get("remarks").toString()
                : "";
        Map<String, Object> result = tariffApprovalService.reject(tpName, remarks);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/approved/list")
    public Map<String, Object> getApprovedList() {
        return jsonStorage.readApproved();
    }

    @GetMapping("/rejected/list")
    public Map<String, Object> getRejectedList() {
        return jsonStorage.readRejected();
    }

    @PostMapping("/rejected/delete/{tpName}")
    public ResponseEntity<?> deleteRejected(
            @PathVariable String tpName,
            HttpSession session) {

        String username = (String) session.getAttribute("username");
        if (username == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        jsonStorage.removeRejected(tpName);
        logger.info("Rejected TP removed after re-submission tpName={} username={}", tpName, username);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/saved/list")
    public Map<String, Object> getSavedList(HttpSession session) {

        String username = (String) session.getAttribute("username");
        return jsonStorage.getByUser(username);
    }

    @PostMapping("/saved/delete/{tpName}")
    public ResponseEntity<?> deleteSaved(
            @PathVariable String tpName,
            HttpSession session) {

        String username = (String) session.getAttribute("username");

        if (username == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        jsonStorage.remove(tpName);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/draft/save")
    public ResponseEntity<?> saveDraft(
            @RequestBody(required = false) String draftJson,
            HttpSession session) {

        if (draftJson == null || draftJson.isBlank()) {
            return ResponseEntity.ok().build();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> draft = mapper.readValue(draftJson, Map.class);

            // prefer session username, fall back to payload username, then guest
            String username = (String) session.getAttribute("username");
            if (username == null) {
                username = (String) draft.get("username");
            }
            if (username == null) {
                username = "guest";
            }

            saveConfigService.saveDraft(draft, username);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/draft/list")
    public List<Map<String, Object>> getDrafts(HttpSession session) {

        String username = (String) session.getAttribute("username");

        if (username == null) {
            username = "guest";
        }

        try {
            Path path = Paths.get("drafts", username + ".json");

            if (!Files.exists(path))
                return new ArrayList<>();

            return new ObjectMapper().readValue(
                    path.toFile(),
                    new TypeReference<List<Map<String, Object>>>() {
                    });

        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @PostMapping("/description")
    public Map<String, String> getDescription(@RequestBody Map<String, Object> request) {

        Long servicePackageId = Long.valueOf(request.get("servicePackageId").toString());

        Long networkId = Long.valueOf(request.get("networkId").toString());

        String desc = servicePackageService.getDescription(servicePackageId, networkId);

        // FIX → handle null
        if (desc == null) {
            desc = "Description not found";
        }
        return Map.of("description", desc);
    }

    @GetMapping("/tariff-package-details")
    public ResponseEntity<List<TariffPackageDetailsDto>> getTariffPackageDetails(
            @RequestParam Integer networkId) {

        List<TariffPackageDetailsDto> response = tariffPackageService.getTariffPackageDetails(networkId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/details")
    public ResponseEntity<?> getTariffPackageDetailsById(@RequestParam Long networkId, @RequestParam Long tariffPackageId) {

        return ResponseEntity.ok(tariffUpdateService.getTariffPackageDetails(tariffPackageId, networkId));
    }

    @GetMapping("/tariffpacks")
    public ResponseEntity<List<TariffPackageDetailsDto>> getTariffPackages(
            @RequestParam Integer networkId) {

        List<TariffPackageDetailsDto> response = tariffPackageService.getTariffPackages(networkId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/update/{tariffPackageId}")
    public ResponseEntity<Map<String, Object>> updateTariffPackage(
            @PathVariable Long tariffPackageId,
            @RequestParam Long networkId,
            @RequestBody Map<String, Object> requestBody,
            HttpSession session) {

        String username = (String) session.getAttribute("username");
        Map<String, Object> result = tariffPackageSyncService.syncTariffPackage(
                tariffPackageId, networkId, requestBody, username != null ? username : "system");
        return ResponseEntity.ok(result);
    }
}
