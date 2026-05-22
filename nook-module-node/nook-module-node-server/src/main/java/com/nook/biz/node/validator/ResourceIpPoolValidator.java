package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpTypeDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpPoolMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceIpTypeMapper;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * IP 池条目业务校验.
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class ResourceIpPoolValidator {

    private final ResourceIpPoolMapper resourceIpPoolMapper;
    private final ResourceIpTypeMapper resourceIpTypeMapper;

    /**
     * 校验 IP 池条目存在.
     *
     * @param id resource_ip_pool.id
     * @return ResourceIpPoolDO
     */
    public ResourceIpPoolDO validateExists(String id) {
        ResourceIpPoolDO e = resourceIpPoolMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_NOT_FOUND, id);
        }
        return e;
    }

    /**
     * 校验 IP 类型存在.
     *
     * @param ipTypeId resource_ip_type.id
     */
    public void validateIpTypeExists(String ipTypeId) {
        ResourceIpTypeDO type = resourceIpTypeMapper.selectById(ipTypeId);
        if (ObjectUtil.isNull(type)) {
            throw new BusinessException(ResourceErrorCode.IP_TYPE_NOT_FOUND, ipTypeId);
        }
    }

    /**
     * 校验 IP 地址在全局唯一; id 用于排除自身 (Update), Create 传 null.
     *
     * @param id        当前行 id (Create 传 null)
     * @param ipAddress IP 地址
     */
    public void validateIpAddressUnique(String id, String ipAddress) {
        if (id == null) {
            if (ObjectUtil.isNotNull(resourceIpPoolMapper.selectByIpAddress(ipAddress))) {
                throw new BusinessException(ResourceErrorCode.IP_POOL_IP_DUPLICATE, ipAddress);
            }
            return;
        }
        if (resourceIpPoolMapper.existsByIpAddressExcludingId(ipAddress, id)) {
            throw new BusinessException(ResourceErrorCode.IP_POOL_IP_DUPLICATE, ipAddress);
        }
    }
}
