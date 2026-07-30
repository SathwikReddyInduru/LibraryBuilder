package com.xius.TariffBuilder.Dto;

public class ServiceMetaDataResponse {

    private Long serviceId;
    private String serviceName;

    public ServiceMetaDataResponse() {
    }

    public ServiceMetaDataResponse(Long serviceId, String serviceName) {
        this.serviceId = serviceId;
        this.serviceName = serviceName;
    }

    public Long getServiceId() {
        return serviceId; 		
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
