package com.nook.biz.agent.validator;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.agent.constant.AgentErrorCode;
import com.nook.biz.agent.controller.vo.AgentInstallReqVO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
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
     * 校验装机角色取值合法
     *
     * @param role 角色
     */
    public void validateRole(String role) {
        if (!AgentRole.isValid(role)) {
            throw new BusinessException(AgentErrorCode.INSTALL_ROLE_INVALID, role);
        }
    }

    /**
     * 校验服务器类型与装机角色一致 (防把落地机当线路机装)
     *
     * @param srv  装机源服务器
     * @param role 装机角色
     */
    public void validateServerType(ResourceServerRespDTO srv, AgentRole role) {
        if (StrUtil.isBlank(srv.getServerType()) || !srv.getServerType().equalsIgnoreCase(role.getCode())) {
            throw new BusinessException(AgentErrorCode.SERVER_TYPE_MISMATCH,
                    srv.getName(), srv.getServerType(), role.getCode());
        }
    }

    /**
     * 校验线路机装机前置: agent_token 已签发且 xray 参数齐全
     *
     * @param srv   装机源服务器
     * @param reqVO 装机参数
     */
    public void validateFrontlinePrerequisite(ResourceServerRespDTO srv, AgentInstallReqVO reqVO) {
        if (StrUtil.isBlank(srv.getAgentToken())) {
            throw new BusinessException(AgentErrorCode.AGENT_TOKEN_MISSING, srv.getName());
        }
        if (StrUtil.isBlank(reqVO.getXrayBin()) || reqVO.getXrayApiPort() == null) {
            throw new BusinessException(AgentErrorCode.FRONTLINE_XRAY_REQUIRED, srv.getName());
        }
    }

    /**
     * 校验落地机装机前置: agent_token 已签发
     *
     * @param srv 装机源服务器
     */
    public void validateLandingPrerequisite(ResourceServerRespDTO srv) {
        if (StrUtil.isBlank(srv.getAgentToken())) {
            throw new BusinessException(AgentErrorCode.AGENT_TOKEN_MISSING, srv.getName());
        }
    }
}
