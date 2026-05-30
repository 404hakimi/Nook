package com.nook.biz.agent.service;

import cn.hutool.core.util.StrUtil;
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
public class AgentAuthService {

    @Resource
    private ResourceServerApi resourceServerApi;

    /**
     * 校验 token 并获得 server
     *
     * @param agentToken X-Agent-Token header
     * @return server 对象
     */
    public ResourceServerRespDTO verifyAndGetServer(String agentToken) {
        if (StrUtil.isBlank(agentToken)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        ResourceServerRespDTO srv = resourceServerApi.getByAgentToken(agentToken);
        if (srv == null) {
            // 不告知 token 是否存在, 统一返 401 避免 token 枚举
            log.warn("[verifyAndGetServer] token 校验失败 tokenPrefix={}",
                    StrUtil.subPre(agentToken, 8));
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return srv;
    }
}
