package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerFrontlineDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerFrontlineMapper;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 线路机扩展业务校验
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class ResourceServerFrontlineValidator {

    private final ResourceServerFrontlineMapper frontlineMapper;

    /**
     * 校验线路机扩展存在
     *
     * @param serverId 服务器编号
     * @return 线路机扩展 DO
     */
    public ResourceServerFrontlineDO validateExists(String serverId) {
        ResourceServerFrontlineDO row = frontlineMapper.selectById(serverId);
        if (ObjectUtil.isNull(row)) {
            throw new BusinessException(ResourceErrorCode.SERVER_NOT_FOUND, serverId);
        }
        return row;
    }

    /**
     * 校验 domain 唯一
     *
     * @param serverId 当前 server 编号; null 表示 Create 路径
     * @param domain   待校验 domain; 空跳过
     */
    public void validateDomainUnique(String serverId, String domain) {
        if (StrUtil.isBlank(domain)) return;
        boolean dup = serverId == null
                ? frontlineMapper.existsByDomain(domain)
                : frontlineMapper.existsByDomainExcludingId(domain, serverId);
        if (dup) {
            throw new BusinessException(ResourceErrorCode.SERVER_DOMAIN_DUPLICATE, domain);
        }
    }
}
