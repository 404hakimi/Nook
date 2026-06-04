package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerFrontlineDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerFrontlineMapper;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 线路机扩展业务校验
 *
 * @author nook
 */
@Component
public class ResourceServerFrontlineValidator {

    @Resource
    private ResourceServerFrontlineMapper resourceServerFrontlineMapper;

    /**
     * 校验线路机扩展存在
     *
     * @param serverId 服务器ID
     * @return ResourceServerFrontlineDO
     */
    public ResourceServerFrontlineDO validateExists(String serverId) {
        ResourceServerFrontlineDO row = resourceServerFrontlineMapper.selectById(serverId);
        if (ObjectUtil.isNull(row)) {
            throw new BusinessException(ResourceErrorCode.SERVER_NOT_FOUND, serverId);
        }
        return row;
    }

    /**
     * 校验域名唯一
     *
     * @param serverId 当前服务器ID (新增传 null 表示不排除自身)
     * @param domain   待校验域名; 空跳过
     */
    public void validateDomainUnique(String serverId, String domain) {
        if (StrUtil.isBlank(domain)) return;
        boolean dup = ObjectUtil.isNull(serverId)
                ? resourceServerFrontlineMapper.existsByDomain(domain)
                : resourceServerFrontlineMapper.existsByDomainExcludingId(domain, serverId);
        if (dup) {
            throw new BusinessException(ResourceErrorCode.SERVER_DOMAIN_DUPLICATE, domain);
        }
    }
}
