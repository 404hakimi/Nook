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
     * 校验 role
     *
     * @param role 角色 code
     */
    public void validateRole(String role) {
        if (!AgentRole.isValid(role)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "role 只能是 frontline / landing");
        }
    }

    /**
     * 装机前置校验: agent_token 由 createServer 签发必须存在; frontline 必须带 xray bin + apiPort.
     *
     * @param srv   resource_server 行 (已校验存在)
     * @param reqVO 装机表单
     */
    public void validateInstallPrerequisite(ResourceServerRespDTO srv, AgentInstallReqVO reqVO) {
        if (StrUtil.isBlank(srv.getAgentToken())) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "server " + srv.getName() + " 缺 agent_token; 用 UPDATE resource_server SET agent_token=... 补一个");
        }
        if (AgentRole.FRONTLINE.getCode().equals(reqVO.getRole())
                && (StrUtil.isBlank(reqVO.getXrayBin()) || reqVO.getXrayApiPort() == null)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID,
                    "frontline 装机必须传 xrayBin + xrayApiPort (server " + srv.getName() + " 可能未装 xray)");
        }
    }
}
