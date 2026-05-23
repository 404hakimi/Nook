package com.nook.biz.agent.validator;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.agent.api.enums.AgentHostType;
import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.agent.controller.vo.AgentInstallReqVO;
import com.nook.biz.node.api.resource.dto.ResourceIpPoolRespDTO;
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
     * 校验 role + hostType 组合; frontline ↔ SERVER, landing ↔ IP_POOL.
     *
     * @param role     角色 code
     * @param hostType 主机表
     */
    public void validateRoleHostMatch(String role, AgentHostType hostType) {
        if (AgentRole.FRONTLINE.getCode().equals(role) && hostType != AgentHostType.SERVER) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID,
                    "frontline 装机 hostType 必须是 SERVER");
        }
        if (AgentRole.LANDING.getCode().equals(role) && hostType != AgentHostType.IP_POOL) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID,
                    "landing 装机 hostType 必须是 IP_POOL");
        }
    }

    /**
     * Frontline 前置: agent_token 必存在 + xrayBin/xrayApiPort 必填.
     *
     * @param srv   resource_server 行 (已校验存在)
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
     * @param ip resource_ip_pool 行 (已校验存在)
     */
    public void validateLandingPrerequisite(ResourceIpPoolRespDTO ip) {
        if (StrUtil.isBlank(ip.getAgentToken())) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "ip_pool " + ip.getIpAddress() + " 缺 agent_token; 用 UPDATE resource_ip_pool SET agent_token=... 补一个");
        }
    }
}
