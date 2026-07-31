package com.xius.TariffBuilder.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xius.TariffBuilder.Dto.ServicePackageDetailDto;
import com.xius.TariffBuilder.Dto.ServicePackageDetailRequest;
import com.xius.TariffBuilder.Dto.ServicePackageDto;
import com.xius.TariffBuilder.Dto.ServicePackageDto1;
import com.xius.TariffBuilder.UserService.ServicePackage1;

@RestController
@RequestMapping("/service-packages")
public class ServicePackageController {
	private static final Logger logger = LoggerFactory.getLogger(ServicePackageController.class);
	    @Autowired
	    private ServicePackage1 servicePackageService;
       
	    @GetMapping("/{networkId}")
	    public ResponseEntity<Map<String, Object>> getServicePackages(
	            @PathVariable Long networkId) {

	        List<ServicePackageDto> servicePackages =
	                servicePackageService.getServicePackages(networkId);

	        Map<String, Object> response = new HashMap<>();

	        if (servicePackages.isEmpty()) {
	            response.put("status", "SUCCESS");
	            response.put("message", "No service packages found.");
	            response.put("data", servicePackages);
	        } else {
	            response.put("status", "SUCCESS");
	            response.put("message", "Service packages fetched successfully.");
	            response.put("data", servicePackages);
	        }

	        return ResponseEntity.ok(response);
	    }
	    
	    
//	    @PostMapping("/service-package-detail")
//	    public ResponseEntity<ServicePackageDetailDto> getServicePackageDetail(
//	            @RequestBody ServicePackageDetailRequest request) {
//
//	        //logger.info("POST /service-plans/service-package-detail called with networkId={}, servicePackageId={}, monthYear={}",
//	               // request.getNetworkId(), request.getServicePackageId(), request.getMonthYear());
//
//	        ServicePackageDetailDto response = servicePackageService.getServicePackageDetail(
//	                request.getNetworkId(), request.getServicePackageId(), request.getMonthYear());
//
//	        return ResponseEntity.ok(response);
//	    } 
	    
	    
	    
	    @GetMapping("/service-packages/AATP")
	    public ResponseEntity<Map<String, Object>> getServicePackages1(
	            @RequestParam Long networkId) {

	        //logger.info("GET /service-plans/service-packages called with networkId={}", networkId);

	        List<ServicePackageDto1> servicePackages =
	                servicePackageService.getServicePackages1(networkId);

	        Map<String, Object> response = new HashMap<>();

	        if (servicePackages.isEmpty()) {
	            response.put("status", "SUCCESS");
	            response.put("message", "No service packages found.");
	            response.put("data", servicePackages);
	        } else {
	            response.put("status", "SUCCESS");
	            response.put("message", "Service packages fetched successfully.");
	            response.put("data", servicePackages);
	        }

	        return ResponseEntity.ok(response);
	    }
	

 @PostMapping("/service-package-detail")
public ResponseEntity<ServicePackageDetailDto> getServicePackageDetail(
        @RequestBody ServicePackageDetailRequest request) {

    //logger.info("POST /service-plans/service-package-detail called with networkId={}, servicePackageId={}, monthYear={}",
           // request.getNetworkId(), request.getServicePackageId(), request.getMonthYear());

    ServicePackageDetailDto response = servicePackageService.getServicePackageDetail(
            request.getNetworkId(), request.getServicePackageId(), request.getMonthYear());

    return ResponseEntity.ok(response);
} 
 }