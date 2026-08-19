package com.xius.Lb.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.xius.Lb.Dto.AtpDetailsResponse;
import com.xius.Lb.Dto.AtpModifyResponse;
import com.xius.Lb.Dto.AtpRequest;
import com.xius.Lb.Dto.AtpResponse;
import com.xius.Lb.service.AtpDetailsService;
import com.xius.Lb.service.AtpService;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/atp")
public class AtpController {

    private final AtpService atpService;
    private final AtpDetailsService atpDetailsService;

    public AtpController(AtpService atpService, AtpDetailsService atpDetailsService) {
        this.atpService = atpService;
        this.atpDetailsService = atpDetailsService;
    }

    @PostMapping("/create")
    public ResponseEntity<AtpResponse> createAtp(
            @RequestBody AtpRequest request) {

        AtpResponse response =
                atpService.createAtp(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{atpId}")
    public ResponseEntity<AtpDetailsResponse> getAtp(
            @PathVariable Long atpId) {

        AtpDetailsResponse response =
                atpDetailsService.getAtpDetails(atpId);

        return ResponseEntity
                .ok(response);
    }

    @PutMapping("modify/{atpId}")
    public ResponseEntity<AtpModifyResponse> modifyAtp(
            @PathVariable Long atpId,
            @RequestBody AtpRequest request) {

        AtpModifyResponse response =
                atpService.modifyAtp(atpId, request);

        return ResponseEntity
                .ok(response);
    }
}