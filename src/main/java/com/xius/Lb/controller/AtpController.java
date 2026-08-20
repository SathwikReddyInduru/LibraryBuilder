package com.xius.Lb.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.xius.Lb.Dto.AtpDetailsResponse;
import com.xius.Lb.Dto.AtpListItem;
import com.xius.Lb.Dto.AtpModifyResponse;
import com.xius.Lb.Dto.AtpRequest;
import com.xius.Lb.Dto.AtpResponse;
import com.xius.Lb.Dto.BucketUsageTypeResponse;
import com.xius.Lb.Dto.CalendarResponse;
import com.xius.Lb.Dto.DerivedServiceResponse;
import com.xius.Lb.Dto.ZoneGroupResponse;
import com.xius.Lb.service.AtpDetailsService;
import com.xius.Lb.service.AtpService;
import com.xius.Lb.service.LibraryGetService;

import jakarta.servlet.http.HttpSession;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/atp")
public class AtpController {

    private final AtpService atpService;
    private final AtpDetailsService atpDetailsService;
    private final LibraryGetService libraryGetService;

    public AtpController(AtpService atpService, AtpDetailsService atpDetailsService, LibraryGetService libraryGetService) {
        this.atpService = atpService;
        this.atpDetailsService = atpDetailsService;
        this.libraryGetService = libraryGetService;
    }

    @GetMapping("/list")
    public ResponseEntity<List<AtpListItem>> getAllAtps(
            @RequestParam Long networkId) {

        List<AtpListItem> response =
                atpService.getAllAtps(networkId);

        return ResponseEntity
                .ok(response);
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

    @GetMapping("/derived-services")
    public ResponseEntity<List<DerivedServiceResponse>> getDerivedServices() {
        return ResponseEntity.ok(libraryGetService.getDerivedServices());
    }

    @GetMapping("/bucket-usage-types")
    public ResponseEntity<List<BucketUsageTypeResponse>> getBucketUsageTypes() {
        return ResponseEntity.ok(libraryGetService.getBucketUsageTypes());
    } 

    @GetMapping("/calendars")
    public ResponseEntity<List<CalendarResponse>> getCalendars(HttpSession session) {
        Long networkId= (Long)session.getAttribute("networkId");
        return ResponseEntity.ok(libraryGetService.getCalendars(networkId));
    }

    @GetMapping("/zone-groups/data")
    public ResponseEntity<List<ZoneGroupResponse>> getDataZoneGroups(HttpSession session) {
        Long networkId = (Long) session.getAttribute("networkId");
        return ResponseEntity.ok(libraryGetService.getDataZoneGroups(networkId));
    }

    @GetMapping("/zone-groups/voice-sms")
    public ResponseEntity<List<ZoneGroupResponse>> getVoiceSmsZoneGroups(HttpSession session) {
        Long networkId = (Long) session.getAttribute("networkId");
        return ResponseEntity.ok(libraryGetService.getVoiceSmsZoneGroups(networkId));
    }
}