package com.nook.biz.node.validator;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.entity.XrayInstallDO;
import com.nook.biz.node.framework.xray.inbound.InboundParams;
import com.nook.biz.node.framework.xray.inbound.InboundParamsResolver;
import com.nook.biz.node.framework.xray.inbound.InboundProtocolFactory;
import com.nook.biz.node.framework.xray.inbound.InboundSetupSpec;
import com.nook.biz.node.service.xray.config.XrayInboundService;
import com.nook.biz.node.service.xray.server.XrayInstallService;
import com.nook.biz.trade.api.SubscriptionCertApi;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Xray 实例 + 配置业务校验
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayInstallValidator {

    @Resource
    private XrayInstallService xrayInstallService;
    @Resource
    private XrayInboundService xrayInboundService;
    @Resource
    private SubscriptionCertApi subscriptionCertApi;
    @Resource
    private InboundProtocolFactory inboundProtocolFactory;

    /**
     * 校验 xray 实例存在并返回
     *
     * @param serverId 服务器ID
     * @return XrayInstallDO
     */
    public XrayInstallDO validateExists(String serverId) {
        XrayInstallDO row = xrayInstallService.get(serverId);
        if (ObjectUtil.isNull(row)) {
            throw new BusinessException(XrayErrorCode.SERVER_STATE_NOT_FOUND, serverId);
        }
        return row;
    }

    /**
     * 重装改客户面参数时记审计日志 (不阻断)
     *
     * <p>共享端口 / ws 路径 / 域名等变更会让在用客户连不上, 需各自重新拉取订阅才能恢复;
     * 是否变更 (如域名轮换) 由运营自行决策, 故这里只告警留痕不拒绝。首次部署或无客户端则跳过。
     *
     * @param serverId 服务器ID
     * @param spec     入站配置规格
     */
    public void warnIfClientFacingChange(String serverId, InboundSetupSpec spec) {
        long activeCount = subscriptionCertApi.listActiveByServerInGroup(serverId).size();

        XrayInstallDO existingServer = xrayInstallService.get(serverId);
        if (ObjectUtil.isNull(existingServer) || activeCount == 0) return;

        XrayInboundDO existingConfig = xrayInboundService.get(serverId);
        if (ObjectUtil.isNull(existingConfig)) return;

        List<String> mismatches = new ArrayList<>();
        // 通用客户面字段: 监听端口 / 监听 IP
        if (!ObjectUtil.equal(existingConfig.getSharedInboundPort(), spec.getSharedInboundPort())) {
            mismatches.add("sharedInboundPort: " + existingConfig.getSharedInboundPort()
                    + " → " + spec.getSharedInboundPort());
        }
        // 协议形态变了 = 全盘客户面变更; 没变则下放给协议实现做协议特定 diff (ws/域名/reality 密钥等)
        XrayInboundProtocolEnum existingProto = XrayInboundProtocolEnum.fromKey(existingConfig.getProtocolKey());
        String existingProtocol = existingProto == null ? null : existingProto.getProtocol();
        if (!ObjectUtil.equal(existingProtocol, spec.getProtocol())) {
            mismatches.add("protocol: " + existingProtocol + " → " + spec.getProtocol());
        } else {
            InboundParams existingParams = InboundParamsResolver.resolve(
                    existingConfig.getProtocolKey(), existingConfig.getParams());
            mismatches.addAll(inboundProtocolFactory.resolve(spec).clientFacingDiff(existingParams, spec));
        }
        if (CollUtil.isNotEmpty(mismatches)) {
            log.warn("[xray-reinstall] server={} 改动客户面参数, {} 个在用客户需重新拉取订阅: {}",
                    serverId, activeCount, String.join("; ", mismatches));
        }
    }
}
