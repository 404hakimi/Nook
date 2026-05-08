package com.nook.biz.resource.service.impl;

import com.nook.biz.resource.api.ResourceIpPoolApi;
import com.nook.biz.resource.api.dto.IpPoolEntryDTO;
import com.nook.biz.resource.entity.ResourceIpPool;
import com.nook.biz.resource.service.ResourceIpPoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResourceIpPoolApiImpl implements ResourceIpPoolApi {

    private final ResourceIpPoolService resourceIpPoolService;

    @Override
    public IpPoolEntryDTO pickAvailable(String region, String ipTypeId, String memberUserId) {
        return toDto(resourceIpPoolService.occupyOne(region, ipTypeId, memberUserId));
    }

    @Override
    public void releaseToCooling(String ipId) {
        resourceIpPoolService.releaseToCooling(ipId);
    }

    @Override
    public IpPoolEntryDTO loadEntry(String ipId) {
        return toDto(resourceIpPoolService.findById(ipId));
    }

    private IpPoolEntryDTO toDto(ResourceIpPool e) {
        if (e == null) return null;
        return IpPoolEntryDTO.builder()
                .id(e.getId())
                .region(e.getRegion())
                .ipTypeId(e.getIpTypeId())
                .ipAddress(e.getIpAddress())
                .socks5Host(e.getSocks5Host())
                .socks5Port(e.getSocks5Port())
                .socks5Username(e.getSocks5Username())
                .socks5Password(e.getSocks5Password())
                .build();
    }
}
