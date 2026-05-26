package com.nook.biz.system.service.region.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.system.dal.dataobject.region.SystemRegionDO;
import com.nook.biz.system.dal.mysql.mapper.region.SystemRegionMapper;
import com.nook.biz.system.service.region.SystemRegionService;
import com.nook.common.utils.collection.CollectionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 区域字典 Service 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class SystemRegionServiceImpl implements SystemRegionService {

    private final SystemRegionMapper systemRegionMapper;

    @Override
    public List<SystemRegionDO> listEnabled() {
        return systemRegionMapper.selectEnabled();
    }

    @Override
    public List<SystemRegionDO> list(String keyword, Integer enabled) {
        return systemRegionMapper.selectByQuery(keyword, enabled);
    }

    @Override
    public SystemRegionDO getByCode(String code) {
        return systemRegionMapper.selectById(code);
    }

    @Override
    public boolean exists(String code) {
        return systemRegionMapper.exists(Wrappers.<SystemRegionDO>lambdaQuery()
                .eq(SystemRegionDO::getCode, code));
    }

    @Override
    public Map<String, String> loadDisplayNameMap(Collection<String> codes) {
        if (CollUtil.isEmpty(codes)) return Collections.emptyMap();
        List<SystemRegionDO> rows = systemRegionMapper.selectBatchIds(codes);
        return CollectionUtils.convertMap(rows, SystemRegionDO::getCode, SystemRegionDO::getDisplayName);
    }
}
