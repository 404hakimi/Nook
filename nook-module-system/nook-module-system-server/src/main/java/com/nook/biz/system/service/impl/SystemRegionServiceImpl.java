package com.nook.biz.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.system.api.region.event.RegionRecodedEvent;
import com.nook.biz.system.controller.region.vo.SystemRegionCreateReqVO;
import com.nook.biz.system.controller.region.vo.SystemRegionRecodeReqVO;
import com.nook.biz.system.controller.region.vo.SystemRegionUpdateReqVO;
import com.nook.biz.system.entity.SystemRegionDO;
import com.nook.biz.system.mapper.SystemRegionMapper;
import com.nook.biz.system.service.SystemRegionService;
import com.nook.biz.system.validator.SystemRegionValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class SystemRegionServiceImpl implements SystemRegionService {

    @Resource
    private SystemRegionMapper systemRegionMapper;
    @Resource
    private SystemRegionValidator systemRegionValidator;
    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public String create(SystemRegionCreateReqVO reqVO) {
        // 校验区域码未被占用
        systemRegionValidator.validateCodeAvailable(reqVO.getCode());
        // 插入区域; 新建默认启用
        SystemRegionDO entity = BeanUtils.toBean(reqVO, SystemRegionDO.class);
        entity.setEnabled(1);
        systemRegionMapper.insert(entity);
        return entity.getCode();
    }

    @Override
    public void update(String code, SystemRegionUpdateReqVO reqVO) {
        // 校验区域存在
        systemRegionValidator.validateExists(code);
        // 更新展示字段; 区域码 (主键) 不可改, 启用状态走 toggleEnabled
        systemRegionMapper.updateFields(code, reqVO.getCountryCode(), reqVO.getCountryName(),
                reqVO.getCity(), reqVO.getDisplayName(), reqVO.getFlagEmoji());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recode(SystemRegionRecodeReqVO reqVO) {
        String oldCode = reqVO.getOldCode();
        String newCode = reqVO.getCode();
        // 校验原区域存在
        systemRegionValidator.validateExists(oldCode);
        // 改主键码并通知各模块迁移引用 (同步监听, 同一事务, 任一失败整体回滚)
        if (!StrUtil.equals(oldCode, newCode)) {
            systemRegionValidator.validateCodeAvailable(newCode);
            systemRegionMapper.renameCode(oldCode, newCode);
            applicationEventPublisher.publishEvent(new RegionRecodedEvent(oldCode, newCode));
        }
        // 其余展示字段以新码为主键更新
        systemRegionMapper.updateFields(newCode, reqVO.getCountryCode(), reqVO.getCountryName(),
                reqVO.getCity(), reqVO.getDisplayName(), reqVO.getFlagEmoji());
    }

    @Override
    public void toggleEnabled(String code, boolean enabled) {
        // 校验区域存在
        systemRegionValidator.validateExists(code);
        // 更新启用状态
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
    public boolean exists(String code) {
        return systemRegionMapper.existsByCode(code);
    }

    @Override
    public Map<String, String> getDisplayNameMap(Collection<String> codes) {
        if (CollUtil.isEmpty(codes)) {
            return Collections.emptyMap();
        }
        // 批量查区域
        List<SystemRegionDO> regions = systemRegionMapper.selectBatchIds(codes);
        // 提取区域码 → 展示名
        return CollectionUtils.convertMap(regions, SystemRegionDO::getCode, SystemRegionDO::getDisplayName);
    }
}
