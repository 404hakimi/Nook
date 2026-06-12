package com.nook.biz.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.system.constant.SystemErrorCode;
import com.nook.biz.system.entity.SystemIpTypeDO;
import com.nook.biz.system.mapper.SystemIpTypeMapper;
import com.nook.biz.system.service.SystemIpTypeService;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * IP 类型 Service 实现类
 *
 * @author nook
 */
@Service
public class SystemIpTypeServiceImpl implements SystemIpTypeService {

    @Resource
    private SystemIpTypeMapper systemIpTypeMapper;

    @Override
    public List<SystemIpTypeDO> getIpTypeList() {
        return systemIpTypeMapper.selectAllOrdered();
    }

    @Override
    public SystemIpTypeDO getIpType(String id) {
        SystemIpTypeDO type = systemIpTypeMapper.selectById(id);
        if (ObjectUtil.isNull(type)) {
            throw new BusinessException(SystemErrorCode.IP_TYPE_NOT_FOUND, id);
        }
        return type;
    }

    @Override
    public Map<String, String> getNameMap(Collection<String> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyMap();
        }
        // 批量查 IP 类型
        List<SystemIpTypeDO> types = systemIpTypeMapper.selectBatchIds(ids);
        // 提取ID → 展示名
        return CollectionUtils.convertMap(types, SystemIpTypeDO::getId, SystemIpTypeDO::getName);
    }
}
