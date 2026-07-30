package com.xius.TariffBuilder.Controller;


import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xius.TariffBuilder.Dto.ServiceMetaDataResponse;
import com.xius.TariffBuilder.repository.ServiceMetaDataRepository;



@RestController
@RequestMapping("/api/services")
public class ServiceMetaDataController {

    @Autowired
    private ServiceMetaDataRepository servicerepo;

    @GetMapping
    public ResponseEntity<List<ServiceMetaDataResponse>> getServices(
            @RequestParam("serviceName") List<String> serviceNames) {

        return ResponseEntity.ok(servicerepo.getServices(serviceNames));
    }
}
