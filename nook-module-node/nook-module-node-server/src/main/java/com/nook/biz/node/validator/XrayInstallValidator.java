package com.nook.biz.node.validator;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import com.nook.biz.node.dal.dataobject.node.XrayInboundDO;
import com.nook.biz.node.dal.dataobject.node.XrayInstallDO;
import com.nook.biz.node.framework.xray.XrayConstants;
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
     * 装机入参跨字段校验: 绑定域名 (domainId 非空) 时域名须存在 + tls 路径必填
     *
     * @param reqVO 装机入参
     */
    public void validateInstallReq(String serverId, XrayInstallReqVO reqVO) {
        if (StrUtil.isBlank(reqVO.getDomainId())) return; // 未绑域名 = 不用 TLS, 跳过
        systemDomainApi.getById(reqVO.getDomainId()); // 根域必须存在 (不存在抛)
        if (StrUtil.isBlank(reqVO.getSubdomain())) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID, "绑定域名时二级域名 (subdomain) 必填");
        }
        // 同一根域下二级标签不能跟别的机器撞 (否则两台抢同一 FQDN / 证书 / A 记录)
        if (xrayInstallService.isSubdomainTaken(reqVO.getDomainId(), reqVO.getSubdomain().trim(), serverId)) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID,
                    "该根域下二级域名 '" + reqVO.getSubdomain().trim() + "' 已被其他线路机占用, 请换一个");
        }
        if (StrUtil.isBlank(reqVO.getTlsCertPath()) || StrUtil.isBlank(reqVO.getTlsKeyPath())) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID, "绑定域名时 tlsCertPath / tlsKeyPath 必填");
        }
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

        List<String> mismatches = new ArrayList<>();
        if (!ObjectUtil.equal(existingConfig.getSharedInboundPort(), reqVO.getSharedInboundPort())) {
            mismatches.add("sharedInboundPort: " + existingConfig.getSharedInboundPort()
                    + " → " + reqVO.getSharedInboundPort());
        }
        if (!ObjectUtil.equal(existingConfig.getProtocol(), reqVO.getProtocol())) {
            mismatches.add("protocol: " + existingConfig.getProtocol() + " → " + reqVO.getProtocol());
        }
        if (!ObjectUtil.equal(existingConfig.getTransport(), reqVO.getTransport())) {
            mismatches.add("transport: " + existingConfig.getTransport() + " → " + reqVO.getTransport());
        }
        if (!ObjectUtil.equal(existingConfig.getListenIp(), reqVO.getListenIp())) {
            mismatches.add("listenIp: " + existingConfig.getListenIp() + " → " + reqVO.getListenIp());
        }
        if (!ObjectUtil.equal(existingConfig.getWsPath(), reqVO.getWsPath())) {
            mismatches.add("wsPath: " + existingConfig.getWsPath() + " → " + reqVO.getWsPath());
        }
        // 未绑域名 (domainId 空) 时 domain 落 null; 绑了拼完整 FQDN (二级标签 + 根域)
        String newDomain = StrUtil.isBlank(reqVO.getDomainId()) ? null
                : XrayConstants.fqdn(reqVO.getSubdomain(), systemDomainApi.getById(reqVO.getDomainId()).getDomain());
        if (!ObjectUtil.equal(existingConfig.getDomain(), newDomain)) {
            mismatches.add("domain: " + existingConfig.getDomain() + " → " + newDomain);
        }
        if (CollUtil.isNotEmpty(mismatches)) {
            log.warn("[xray-reinstall] server={} 改动客户面参数, {} 个在用客户需重新拉取订阅: {}",
                    serverId, activeCount, String.join("; ", mismatches));
        }
    }
}
