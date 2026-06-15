package com.nook.biz.node.lifecycle;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.entity.Socks5InstallDO;
import com.nook.biz.node.entity.XrayInstallDO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.framework.socks5.probe.Socks5ProbeSnapshot;
import com.nook.biz.node.framework.socks5.probe.Socks5Prober;
import com.nook.biz.node.mapper.XrayInstallMapper;
import com.nook.biz.node.validator.ResourceServerLandingValidator;
import com.nook.biz.node.validator.ResourceServerValidator;
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

    /** 落地机上线拨测的检查地址 (echo 出口 IP); 经落地机 SOCKS5 访问验证实际出网可用. */
    private static final String PROBE_ECHO_URL = "https://api.ipify.org/";
    /** 拨测 TCP 建连超时毫秒. */
    private static final int PROBE_CONNECT_TIMEOUT_MS = 5000;
    /** 拨测 HTTP 读响应超时毫秒. */
    private static final int PROBE_READ_TIMEOUT_MS = 10000;

    @Resource
    private XrayInstallMapper xrayInstallMapper;
    @Resource
    private ResourceServerLandingValidator resourceServerLandingValidator;
    @Resource
    private ResourceServerValidator resourceServerValidator;
    @Resource
    private Socks5Prober socks5Prober;

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

    /**
     * 校验落地机上线前置: SOCKS5 配置齐全且经其 SOCKS5 拨测检查地址实际出网可用
     *
     * @param server 当前服务器
     */
    public void validateLandingSocks5Ready(ResourceServerDO server) {
        // 落地机存在 + SOCKS5 端口 / 账号 / 密码齐全
        Socks5InstallDO landing = resourceServerLandingValidator.validateExists(server.getId());
        resourceServerLandingValidator.validateSocks5ConfigReady(landing);
        // 经落地机 SOCKS5 拨测检查地址, 验证实际出网可用
        Socks5ProbeSnapshot snapshot = socks5Prober.probe(server.getIpAddress(), landing.getSocks5Port(),
                landing.getSocks5Username(), landing.getSocks5Password(),
                PROBE_ECHO_URL, PROBE_CONNECT_TIMEOUT_MS, PROBE_READ_TIMEOUT_MS);
        if (!snapshot.isSuccess()) {
            throw new BusinessException(ResourceErrorCode.LANDING_SOCKS5_PROBE_FAILED,
                    server.getId(), snapshot.getErrorMessage());
        }
    }

    /**
     * 校验服务器可删: 装机中 / 待上线可直接删; 运行中 / 已退役须未被订阅占用或绑定
     *
     * @param server 当前服务器
     */
    public void validateDeletable(ResourceServerDO server) {
        // 装机中 / 待上线: 未进选址池, 不会被套餐绑定或订阅占用, 直接放行
        if (ResourceServerLifecycleEnum.INSTALLING.matches(server.getLifecycleState())
                || ResourceServerLifecycleEnum.READY.matches(server.getLifecycleState())) {
            return;
        }
        // 运行中 / 已退役: 仍被生效凭证占用 / 绑定则拒绝
        resourceServerValidator.validateNoBoundClient(server.getId());
    }
}
