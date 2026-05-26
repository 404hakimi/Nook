package com.nook.biz.system.service.iptype.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.system.constant.SystemErrorCode;
import com.nook.biz.system.dal.dataobject.iptype.SystemIpTypeDO;
import com.nook.biz.system.dal.mysql.mapper.iptype.SystemIpTypeMapper;
import com.nook.biz.system.service.iptype.SystemIpTypeService;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class SystemIpTypeServiceImpl implements SystemIpTypeService {

    private final SystemIpTypeMapper systemIpTypeMapper;

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
    public Map<String, String> loadNameMap(Collection<String> ids) {
        if (CollUtil.isEmpty(ids)) return Collections.emptyMap();
        List<SystemIpTypeDO> rows = systemIpTypeMapper.selectBatchIds(ids);
        return CollectionUtils.convertMap(rows, SystemIpTypeDO::getId, SystemIpTypeDO::getName);
    }
}
