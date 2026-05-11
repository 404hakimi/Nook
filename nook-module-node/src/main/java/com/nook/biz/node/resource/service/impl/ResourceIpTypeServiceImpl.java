package com.nook.biz.node.resource.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.resource.constant.ResourceErrorCode;
import com.nook.biz.node.resource.entity.ResourceIpType;
import com.nook.biz.node.resource.mapper.ResourceIpTypeMapper;
import com.nook.biz.node.resource.service.ResourceIpTypeService;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResourceIpTypeServiceImpl implements ResourceIpTypeService {

    private final ResourceIpTypeMapper resourceIpTypeMapper;

    @Override
    public List<ResourceIpType> listAll() {
        return resourceIpTypeMapper.selectAllOrdered();
    }

    @Override
    public ResourceIpType findById(String id) {
        ResourceIpType e = resourceIpTypeMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(ResourceErrorCode.IP_TYPE_NOT_FOUND, id);
        }
        return e;
    }
}
