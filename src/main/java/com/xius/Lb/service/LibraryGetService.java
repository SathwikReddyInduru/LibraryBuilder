package com.xius.Lb.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.xius.Lb.Dto.BucketUsageTypeResponse;
import com.xius.Lb.Dto.CalendarResponse;
import com.xius.Lb.Dto.DerivedServiceResponse;
import com.xius.Lb.Dto.ZoneGroupResponse;
import com.xius.Lb.repo.LibraryGetRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LibraryGetService {

private final LibraryGetRepository libraryGetRepository;

    public List<DerivedServiceResponse> getDerivedServices() {
        return libraryGetRepository.getDerivedServices();
    }

    public List<BucketUsageTypeResponse> getBucketUsageTypes() {
        return libraryGetRepository.getBucketUsageTypes();
    }

    public List<CalendarResponse> getCalendars(Long networkId) {
        return libraryGetRepository.getCalendars(networkId);
    }

    public List<ZoneGroupResponse> getVoiceSmsZoneGroups(Long networkId) {
        return libraryGetRepository.getVoiceSmsZoneGroups(networkId);
    }

    public List<ZoneGroupResponse> getDataZoneGroups(Long networkId) {
        return libraryGetRepository.getDataZoneGroups(networkId);
    }
}