package com.nook.biz.system.service.region.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.system.controller.region.vo.SystemRegionSaveReqVO;
import com.nook.biz.system.dal.dataobject.region.SystemRegionDO;
import com.nook.biz.system.dal.mysql.mapper.region.SystemRegionMapper;
import com.nook.biz.system.service.region.SystemRegionService;
import com.nook.biz.system.validator.SystemRegionValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
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
    private final SystemRegionValidator systemRegionValidator;

    @Override
    public String create(SystemRegionSaveReqVO req) {
        systemRegionValidator.validateCodeAvailable(req.getCode());
        SystemRegionDO e = BeanUtils.toBean(req, SystemRegionDO.class);
        e.setEnabled(1); // 新建默认启用
        systemRegionMapper.insert(e);
        return e.getCode();
    }

    @Override
    public void update(SystemRegionSaveReqVO req) {
        systemRegionValidator.validateExists(req.getCode());
        // 区域码(主键)不可改; 城市可清空 → 用显式 set 绕过 NOT_NULL 更新策略; 启用状态走 toggleEnabled
        systemRegionMapper.update(null, Wrappers.<SystemRegionDO>lambdaUpdate()
                .eq(SystemRegionDO::getCode, req.getCode())
                .set(SystemRegionDO::getCountryCode, req.getCountryCode())
                .set(SystemRegionDO::getCountryName, req.getCountryName())
                .set(SystemRegionDO::getCity, req.getCity())
                .set(SystemRegionDO::getDisplayName, req.getDisplayName())
                .set(SystemRegionDO::getFlagEmoji, req.getFlagEmoji()));
    }

    @Override
    public void toggleEnabled(String code, boolean enabled) {
        systemRegionValidator.validateExists(code);
        SystemRegionDO patch = new SystemRegionDO();
        patch.setCode(code);
        patch.setEnabled(enabled ? 1 : 0);
        systemRegionMapper.updateById(patch);
    }

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
