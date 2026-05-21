package com.nook.biz.node.service.resource.impl;

import com.nook.biz.node.dal.dataobject.resource.ResourceRegionDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceRegionMapper;
import com.nook.biz.node.service.resource.ResourceRegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResourceRegionServiceImpl implements ResourceRegionService {

    private final ResourceRegionMapper resourceRegionMapper;

    @Override
    public List<ResourceRegionDO> listEnabled() {
        return resourceRegionMapper.selectEnabled();
    }

    @Override
    public List<ResourceRegionDO> list(String keyword, Integer enabled) {
        return resourceRegionMapper.selectByQuery(keyword, enabled);
    }

    @Override
    public ResourceRegionDO getByCode(String code) {
        return resourceRegionMapper.selectById(code);
    }
}
