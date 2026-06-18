package com.nook.biz.node.validator;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.controller.xray.vo.XrayInboundConfigVO;
import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import com.nook.biz.node.entity.XrayInboundDO;
import com.nook.biz.node.entity.XrayInstallDO;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.framework.xray.inbound.config.InboundParams;
import com.nook.biz.node.service.xray.config.XrayInboundService;
import com.nook.biz.node.service.xray.server.XrayInstallService;
import com.nook.biz.system.api.domain.SystemDomainApi;
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
    private SystemDomainApi systemDomainApi;

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
     * @param reqVO    装机入参
     */
    public void warnIfClientFacingChange(String serverId, XrayInstallReqVO reqVO) {
        long activeCount = subscriptionCertApi.listActiveByServerInGroup(serverId).size();

        XrayInstallDO existingServer = xrayInstallService.get(serverId);
        if (ObjectUtil.isNull(existingServer) || activeCount == 0) return;

        XrayInboundDO existingConfig = xrayInboundService.get(serverId);
        if (ObjectUtil.isNull(existingConfig)) return;

        XrayInboundConfigVO inbound = reqVO.getInbound();
        // 现存形态由 protocol_key 解出, ws/对外域名从 params 取 (旧 protocol/transport/ws_path/domain 列已删)
        XrayInboundProtocolEnum existingProto = XrayInboundProtocolEnum.fromKey(existingConfig.getProtocolKey());
        String existingProtocol = existingProto == null ? null : existingProto.getProtocol();
        String existingTransport = existingProto == null ? null : existingProto.getTransport();
        InboundParams existingParams = StrUtil.isBlank(existingConfig.getParams())
                ? null : JSON.parseObject(existingConfig.getParams(), InboundParams.class);
        String existingWsPath = (existingParams != null && existingParams.getWs() != null)
                ? existingParams.getWs().getPath() : null;
        String existingDomain = (existingParams != null && existingParams.getTls() != null)
                ? existingParams.getTls().getDomain() : null;
        List<String> mismatches = new ArrayList<>();
        if (!ObjectUtil.equal(existingConfig.getSharedInboundPort(), inbound.getSharedInboundPort())) {
            mismatches.add("sharedInboundPort: " + existingConfig.getSharedInboundPort()
                    + " → " + inbound.getSharedInboundPort());
        }
        if (!ObjectUtil.equal(existingProtocol, inbound.getProtocol())) {
            mismatches.add("protocol: " + existingProtocol + " → " + inbound.getProtocol());
        }
        if (!ObjectUtil.equal(existingTransport, inbound.getTransport())) {
            mismatches.add("transport: " + existingTransport + " → " + inbound.getTransport());
        }
        if (!ObjectUtil.equal(existingConfig.getListenIp(), inbound.getListenIp())) {
            mismatches.add("listenIp: " + existingConfig.getListenIp() + " → " + inbound.getListenIp());
        }
        if (!ObjectUtil.equal(existingWsPath, inbound.getWsPath())) {
            mismatches.add("wsPath: " + existingWsPath + " → " + inbound.getWsPath());
        }
        // 未绑域名 (domainId 空) 时 domain 落 null; 绑了拼完整 FQDN (二级标签 + 根域)
        String newDomain = StrUtil.isBlank(inbound.getDomainId()) ? null
                : XrayConstants.fqdn(inbound.getSubdomain(), systemDomainApi.getById(inbound.getDomainId()).getDomain());
        if (!ObjectUtil.equal(existingDomain, newDomain)) {
            mismatches.add("domain: " + existingDomain + " → " + newDomain);
        }
        if (CollUtil.isNotEmpty(mismatches)) {
            log.warn("[xray-reinstall] server={} 改动客户面参数, {} 个在用客户需重新拉取订阅: {}",
                    serverId, activeCount, String.join("; ", mismatches));
        }
    }
}
