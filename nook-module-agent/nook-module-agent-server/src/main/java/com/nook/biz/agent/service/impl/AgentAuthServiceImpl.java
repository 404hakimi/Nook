package com.nook.biz.agent.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.agent.service.AgentAuthService;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent 鉴权 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class AgentAuthServiceImpl implements AgentAuthService {

    @Resource
    private ResourceServerApi resourceServerApi;

    @Override
    public ResourceServerRespDTO verifyAndGetServer(String agentToken) {
        if (StrUtil.isBlank(agentToken)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        // 按 token 查服务器
        ResourceServerRespDTO srv = resourceServerApi.getByAgentToken(agentToken);
        // 不告知 token 是否存在, 统一返 401 避免 token 枚举
        if (ObjectUtil.isNull(srv)) {
            log.warn("[verifyAndGetServer] token 校验失败: tokenPrefix={}", StrUtil.subPre(agentToken, 8));
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return srv;
    }
}
