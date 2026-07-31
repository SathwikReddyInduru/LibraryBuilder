package com.xius.TariffBuilder.Controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xius.TariffBuilder.Dto.BulkRateUpdateRequest;
import com.xius.TariffBuilder.UserService.BulkService;

@RestController
@RequestMapping("/Bulk")
public class BulkController {
	
	@Autowired
	private BulkService bulkService;
	
	@PutMapping("/bulk-rate/update")
    public ResponseEntity<Map<String, Object>> updateBulkRate(
            @RequestBody BulkRateUpdateRequest request) {

        String message = bulkService.updateBulkRate(request);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", message);

        return ResponseEntity.ok(response);
    }
}
