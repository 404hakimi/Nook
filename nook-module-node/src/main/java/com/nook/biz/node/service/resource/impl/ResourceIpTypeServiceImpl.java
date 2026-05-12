package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpTypeDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpTypeMapper;
import com.nook.biz.node.enums.ResourceErrorCode;
import com.nook.biz.node.service.resource.ResourceIpTypeService;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * IP 类型 Service 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class ResourceIpTypeServiceImpl implements ResourceIpTypeService {

    private final ResourceIpTypeMapper resourceIpTypeMapper;

    @Override
    public List<ResourceIpTypeDO> getIpTypeList() {
        return resourceIpTypeMapper.selectAllOrdered();
    }

    @Override
    public ResourceIpTypeDO getIpType(String id) {
        ResourceIpTypeDO type = resourceIpTypeMapper.selectById(id);
        if (ObjectUtil.isNull(type)) {
            throw new BusinessException(ResourceErrorCode.IP_TYPE_NOT_FOUND, id);
        }
        return type;
    }
}
