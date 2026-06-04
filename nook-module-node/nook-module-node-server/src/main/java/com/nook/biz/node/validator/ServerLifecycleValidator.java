package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.api.enums.ResourceServerLandingStatusEnum;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerFrontlineDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerFrontlineMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerLandingMapper;
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
    private ResourceServerLandingMapper resourceServerLandingMapper;
    @Resource
    private ResourceServerLandingValidator resourceServerLandingValidator;

    /** 允许的生命周期流转 (from→to); 未列出的组合一律拒. */
    private static final Set<String> ALLOWED_TRANSITIONS = Set.of(
            "INSTALLING→READY", "READY→INSTALLING",
            "READY→LIVE", "LIVE→READY",
            "LIVE→RETIRED", "READY→RETIRED",
            "RETIRED→LIVE");

    /**
     * 校验生命周期流转 (转移表 + 各前置守卫); 原地不变的流转由调用方先行放过
     *
     * @param server   当前服务器
     * @param newState 目标生命周期
     */
    public void validateTransition(ResourceServerDO server, String newState) {
        String from = server.getLifecycleState();
        if (ObjectUtil.isNull(ResourceServerLifecycleEnum.fromState(newState))
                || !ALLOWED_TRANSITIONS.contains(from + "→" + newState)) {
            throw new BusinessException(ResourceErrorCode.SERVER_LIFECYCLE_INVALID_TRANSITION, from, newState);
        }
        // 上线前置: 仅线路机需填域名 (落地机 SOCKS5 无域名概念)
        if (ResourceServerLifecycleEnum.LIVE.matches(newState)
                && ResourceServerTypeEnum.FRONTLINE.matches(server.getServerType())) {
            this.validateFrontlineDomainReady(server.getId());
        }
        // 停用前置: 在用的落地机不可停用 (占用中 / 仍有客户端绑定)
        if (ResourceServerLifecycleEnum.RETIRED.matches(newState)
                && ResourceServerTypeEnum.LANDING.matches(server.getServerType())) {
            this.validateLandingNotInUse(server.getId());
        }
    }

    /** 线路机上线前必须先填域名. */
    private void validateFrontlineDomainReady(String serverId) {
        ResourceServerFrontlineDO frontline = resourceServerFrontlineMapper.selectById(serverId);
        if (ObjectUtil.isNull(frontline) || StrUtil.isBlank(frontline.getDomain())) {
            throw new BusinessException(ResourceErrorCode.SERVER_LIVE_DOMAIN_REQUIRED);
        }
    }

    /** 落地机停用前: 占用中(已占用/预占中)或仍有客户端绑定则拒 (订阅占用与客户端绑定一般同步, 但客户端可手工管理, 故两个信号都查). */
    private void validateLandingNotInUse(String serverId) {
        ResourceServerLandingDO landing = resourceServerLandingMapper.selectById(serverId);
        String status = ObjectUtil.isNull(landing) ? null : landing.getStatus();
        boolean inUse = ResourceServerLandingStatusEnum.OCCUPIED.matches(status)
                || ResourceServerLandingStatusEnum.RESERVED.matches(status)
                || resourceServerLandingValidator.hasBoundClient(serverId);
        if (inUse) {
            throw new BusinessException(ResourceErrorCode.LANDING_IN_USE_CANNOT_RETIRE);
        }
    }
}
