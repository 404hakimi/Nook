package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDnsDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerDnsMapper;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 服务器 DNS 绑定业务校验
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class ResourceServerDnsValidator {

    private final ResourceServerDnsMapper dnsMapper;

    public ResourceServerDnsDO validateExists(String serverId) {
        ResourceServerDnsDO row = dnsMapper.selectById(serverId);
        if (ObjectUtil.isNull(row)) {
            throw new BusinessException(ResourceErrorCode.SERVER_NOT_FOUND, serverId);
        }
        return row;
    }

    /** domain 为空跳过 (LIVE 前置才必填; LIVE 切换校验在 ResourceServerService.transitionLifecycle 内). */
    public void validateDomainUnique(String serverId, String domain) {
        if (StrUtil.isBlank(domain)) return;
        boolean dup = serverId == null
                ? dnsMapper.existsByDomain(domain)
                : dnsMapper.existsByDomainExcludingId(domain, serverId);
        if (dup) {
            throw new BusinessException(ResourceErrorCode.SERVER_DOMAIN_DUPLICATE, domain);
        }
    }
}
