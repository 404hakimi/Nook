package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerFrontlineDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerFrontlineMapper;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 服务器生命周期流转校验
 *
 * @author nook
 */
@Component
public class ServerLifecycleValidator {

    @Resource
    private ResourceServerFrontlineMapper resourceServerFrontlineMapper;
    @Resource
    private ResourceServerLandingValidator resourceServerLandingValidator;

    /** 允许的生命周期流转 (from→to); 未列出的组合一律拒. */
    private static final Set<String> ALLOWED_TRANSITIONS = Set.of(
            "INSTALLING→READY", "READY→INSTALLING",
            "READY→LIVE", "LIVE→READY",
            "LIVE→RETIRED", "READY→RETIRED",
            "RETIRED→LIVE");

    /**
     * 校验生命周期流转转移表 (from→to 是否允许); 各类型上下线前置守卫由对应 service 另行调用
     *
     * @param server   当前服务器
     * @param newState 目标生命周期
     */
    public void validateTransitionTable(ResourceServerDO server, String newState) {
        String from = server.getLifecycleState();
        if (ObjectUtil.isNull(ResourceServerLifecycleEnum.fromState(newState))
                || !ALLOWED_TRANSITIONS.contains(from + "→" + newState)) {
            throw new BusinessException(ResourceErrorCode.SERVER_LIFECYCLE_INVALID_TRANSITION, from, newState);
        }
    }

    /** 线路机上线前必须先填域名. */
    public void validateFrontlineDomainReady(String serverId) {
        ResourceServerFrontlineDO frontline = resourceServerFrontlineMapper.selectById(serverId);
        if (ObjectUtil.isNull(frontline) || StrUtil.isBlank(frontline.getDomain())) {
            throw new BusinessException(ResourceErrorCode.SERVER_LIVE_DOMAIN_REQUIRED);
        }
    }

    /** 落地机停用前: 仍有生效凭证绑定 (cert.ip_id, 含 ACTIVE/SUSPENDED) 则拒. */
    public void validateLandingNotInUse(String serverId) {
        if (resourceServerLandingValidator.hasBoundClient(serverId)) {
            throw new BusinessException(ResourceErrorCode.LANDING_IN_USE_CANNOT_RETIRE);
        }
    }
}
