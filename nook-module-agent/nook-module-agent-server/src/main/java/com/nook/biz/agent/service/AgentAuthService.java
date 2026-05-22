package com.nook.biz.agent.service;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Agent push 接口的轻量鉴权: 按 X-Agent-Token Header → server. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAuthService {

    private final ResourceServerMapper resourceServerMapper;

    /**
     * 校验 token 并返回 server; 失败抛 UNAUTHORIZED.
     * 调用方在 Controller 入口拿到 X-Agent-Token Header 后调一次.
     */
    public ResourceServerDO verifyAndGetServer(String agentToken) {
        if (StrUtil.isBlank(agentToken)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        ResourceServerDO srv = resourceServerMapper.selectByAgentToken(agentToken);
        if (srv == null) {
            // 不告知 token 是否存在, 统一返 401 避免 token 枚举
            log.warn("[verifyAndGetServer] token 校验失败 tokenPrefix={}",
                    StrUtil.subPre(agentToken, 8));
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return srv;
    }
}
