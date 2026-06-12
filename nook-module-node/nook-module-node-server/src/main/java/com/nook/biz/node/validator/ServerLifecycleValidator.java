package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.entity.XrayInstallDO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.mapper.XrayInstallMapper;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 服务器生命周期流转校验
 *
 * @author nook
 */
@Component
public class ServerLifecycleValidator {

    @Resource
    private XrayInstallMapper xrayInstallMapper;
    @Resource
    private ResourceServerLandingValidator resourceServerLandingValidator;

    /**
     * 校验生命周期流转合法性 (流转表见 {@link ResourceServerLifecycleEnum}); 各类型上下线前置守卫由对应 service 另行调用
     *
     * @param server   当前服务器
     * @param newState 目标生命周期
     */
    public void validateTransitionTable(ResourceServerDO server, String newState) {
        ResourceServerLifecycleEnum from = ResourceServerLifecycleEnum.fromState(server.getLifecycleState());
        ResourceServerLifecycleEnum to = ResourceServerLifecycleEnum.fromState(newState);
        if (from == null || to == null || !from.canTransitionTo(to)) {
            throw new BusinessException(ResourceErrorCode.SERVER_LIFECYCLE_INVALID_TRANSITION,
                    from == null ? server.getLifecycleState() : from.getLabel(),
                    to == null ? newState : to.getLabel());
        }
    }

    /**
     * 校验线路机上线前置: 已装 xray 且已绑定域名
     *
     * @param serverId 服务器ID
     */
    public void validateFrontlineDomainReady(String serverId) {
        XrayInstallDO xray = xrayInstallMapper.selectById(serverId);
        if (ObjectUtil.isNull(xray) || StrUtil.isBlank(xray.getDomainId())) {
            throw new BusinessException(ResourceErrorCode.SERVER_LIVE_DOMAIN_REQUIRED);
        }
    }

    /**
     * 校验落地机停用前置: 仍有生效中或暂停中的凭证绑定则拒绝
     *
     * @param serverId 服务器ID
     */
    public void validateLandingNotInUse(String serverId) {
        if (resourceServerLandingValidator.hasBoundClient(serverId)) {
            throw new BusinessException(ResourceErrorCode.LANDING_IN_USE_CANNOT_RETIRE);
        }
    }
}
