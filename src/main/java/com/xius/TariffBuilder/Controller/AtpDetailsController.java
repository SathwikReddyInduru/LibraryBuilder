package com.xius.TariffBuilder.Controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
 * TODO — NOT IMPLEMENTED YET.
 *
 * These two endpoints back the ATP Library "View details" and "Modify"
 * screens (atpdetails.html / atpdetails.js, atpform.html in edit mode).
 * Both currently return HTTP 501 so the frontend can detect "not built
 * yet" and fall back gracefully instead of treating it as a generic
 * failure (a 404 or 500 would look identical to a real error).
 *
 * When these are ready:
 *   1. Wire getAtpDetails() up to whatever service/repository can resolve
 *      the full ATP configuration for a given servicePackageId — ideally
 *      returning the same shape the create form's payload builder
 *      produces in Atpcreate.js (buildAtpPayload), so atpform.html can
 *      reuse it directly to pre-fill the Modify form.
 *   2. Wire modifyAtp() up to the real update path, then remove the
 *      isEditMode() early-return guards in Atpcreate.js (search for
 *      "Modify API not implemented yet") so Publish/Save Draft submit for
 *      real in edit mode.
 *   3. Remove the 501 handling in atpdetails.js (renderFallback) and the
 *      atpc-modify-notice banner in atpform.html once both are live.
 */
@RestController
@CrossOrigin(origins = "*")
public class AtpDetailsController {

    private static final Logger logger = LoggerFactory.getLogger(AtpDetailsController.class);

    @GetMapping("/builder/atp-details")
    public ResponseEntity<Map<String, Object>> getAtpDetails(
            @RequestParam Long servicePackageId,
            @RequestParam(required = false) Long networkId) {

        logger.warn("GET /builder/atp-details called but not implemented yet. servicePackageId={} networkId={}",
                servicePackageId, networkId);

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of(
                        "message", "ATP details API not implemented yet",
                        "servicePackageId", servicePackageId));
    }

    @PutMapping("/builder/atp-modify/{servicePackageId}")
    public ResponseEntity<Map<String, Object>> modifyAtp(
            @PathVariable Long servicePackageId,
            @RequestParam(required = false) Long networkId,
            @RequestBody(required = false) Map<String, Object> requestBody) {

        logger.warn("PUT /builder/atp-modify/{} called but not implemented yet.", servicePackageId);

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of(
                        "message", "ATP modify API not implemented yet",
                        "servicePackageId", servicePackageId));
    }
}
