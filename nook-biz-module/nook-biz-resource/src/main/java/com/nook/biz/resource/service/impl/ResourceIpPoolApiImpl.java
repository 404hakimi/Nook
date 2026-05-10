package com.nook.biz.resource.service.impl;

import com.nook.biz.resource.api.ResourceIpPoolApi;
import com.nook.biz.resource.api.dto.IpPoolEntryDTO;
import com.nook.biz.resource.entity.ResourceIpPool;
import com.nook.biz.resource.mapper.ResourceIpPoolMapper;
import com.nook.biz.resource.service.ResourceIpPoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ResourceIpPoolApiImpl implements ResourceIpPoolApi {

    private final ResourceIpPoolService resourceIpPoolService;
    /**
     * 批量查 ip_address 走 mapper 的 selectBatchIds; 走 service.findById 一条条调会 N+1, 列表页 page 50 条一查就 50 次 SQL.
     * 这里直绕开 service 层是出于"列表 enrich 路径要快、且不需要校验单条存在性 (缺失记录交给上游容错)" 的考虑.
     */
    private final ResourceIpPoolMapper resourceIpPoolMapper;

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

    @Override
    public Map<String, String> loadIpAddressMap(Collection<String> ipIds) {
        if (ipIds == null || ipIds.isEmpty()) return Collections.emptyMap();
        // 去重避免一次查里塞重复 id; HashSet 顺便剔除 null
        Set<String> dedup = new HashSet<>();
        for (String id : ipIds) {
            if (id != null && !id.isEmpty()) dedup.add(id);
        }
        if (dedup.isEmpty()) return Collections.emptyMap();
        List<ResourceIpPool> rows = resourceIpPoolMapper.selectBatchIds(dedup);
        Map<String, String> out = new HashMap<>(rows.size() * 2);
        for (ResourceIpPool e : rows) {
            out.put(e.getId(), e.getIpAddress());
        }
        return out;
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
