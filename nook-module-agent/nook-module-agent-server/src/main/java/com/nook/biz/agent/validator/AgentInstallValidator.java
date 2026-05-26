package com.nook.biz.agent.validator;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.agent.controller.vo.AgentInstallReqVO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * Agent 装机业务校验
 *
 * @author nook
 */
@Component
public class AgentInstallValidator {

    /**
     * 校验 role 值合法 (frontline / landing).
     *
     * @param role 角色 code
     */
    public void validateRole(String role) {
        if (!AgentRole.isValid(role)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "role 只能是 frontline / landing");
        }
    }

    /**
     * server_type 必须等于 role; 防 admin 把 landing server 当 frontline 装.
     *
     * @param srv  resource_server 行
     * @param role 装机角色
     */
    public void validateServerType(ResourceServerRespDTO srv, AgentRole role) {
        if (StrUtil.isBlank(srv.getServerType()) || !srv.getServerType().equalsIgnoreCase(role.getCode())) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID,
                    "server " + srv.getName() + " server_type=" + srv.getServerType()
                            + " 与装机 role=" + role.getCode() + " 不一致");
        }
    }

    /**
     * Frontline 前置: agent_token 必存在 + xrayBin/xrayApiPort 必填.
     *
     * @param srv   resource_server 行
     * @param reqVO 装机表单
     */
    public void validateFrontlinePrerequisite(ResourceServerRespDTO srv, AgentInstallReqVO reqVO) {
        if (StrUtil.isBlank(srv.getAgentToken())) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "server " + srv.getName() + " 缺 agent_token; 用 UPDATE resource_server SET agent_token=... 补一个");
        }
        if (StrUtil.isBlank(reqVO.getXrayBin()) || reqVO.getXrayApiPort() == null) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID,
                    "frontline 装机必须传 xrayBin + xrayApiPort (server " + srv.getName() + " 可能未装 xray)");
        }
    }

    /**
     * Landing 前置: agent_token 必存在; xray 字段忽略.
     *
     * @param srv resource_server 行 (server_type=landing)
     */
    public void validateLandingPrerequisite(ResourceServerRespDTO srv) {
        if (StrUtil.isBlank(srv.getAgentToken())) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "landing " + srv.getName() + " 缺 agent_token; 用 UPDATE resource_server SET agent_token=... 补一个");
        }
    }
}
