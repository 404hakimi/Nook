package com.nook.biz.node.validator;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.controller.xray.vo.XrayServerInstallReqVO;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
import com.nook.biz.node.dal.dataobject.node.XrayServerDO;
import com.nook.biz.node.service.xray.config.XrayConfigService;
import com.nook.biz.node.service.xray.server.XrayServerService;
import com.nook.biz.trade.api.SubscriptionCertApi;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Xray 实例 + 配置业务校验
 *
 * @author nook
 */
@Component
public class XrayServerValidator {

    @Resource
    private XrayServerService xrayServerService;
    @Resource
    private XrayConfigService xrayConfigService;
    @Resource
    private SubscriptionCertApi subscriptionCertApi;

    /**
     * 校验 xray 实例存在并返回
     *
     * @param serverId 服务器ID
     * @return XrayServerDO
     */
    public XrayServerDO validateExists(String serverId) {
        XrayServerDO row = xrayServerService.get(serverId);
        if (ObjectUtil.isNull(row)) {
            throw new BusinessException(XrayErrorCode.SERVER_STATE_NOT_FOUND, serverId);
        }
        return row;
    }

    /**
     * 装机入参跨字段校验: useTls=true 时 domain / tls 路径必填
     *
     * @param reqVO 装机入参
     */
    public void validateInstallReq(XrayServerInstallReqVO reqVO) {
        if (!Boolean.TRUE.equals(reqVO.getUseTls())) return;
        if (StrUtil.isBlank(reqVO.getDomain())) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID, "useTls=true 时 domain 必填");
        }
        if (StrUtil.isBlank(reqVO.getTlsCertPath()) || StrUtil.isBlank(reqVO.getTlsKeyPath())) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID, "useTls=true 时 tlsCertPath / tlsKeyPath 必填");
        }
    }

    /**
     * 校验装机入参不与现有客户端的客户面参数冲突
     *
     * <p>存在客户端且共享端口 / ws 路径 / 域名等客户面参数变更时拒绝; 首次部署或无客户端则跳过.
     *
     * @param serverId 服务器ID
     * @param reqVO    装机入参
     */
    public void validateAgainstActiveClients(String serverId, XrayServerInstallReqVO reqVO) {
        long activeCount = subscriptionCertApi.listActiveByServer(serverId).size();

        XrayServerDO existingServer = xrayServerService.get(serverId);
        if (ObjectUtil.isNull(existingServer) || activeCount == 0) return;

        XrayConfigDO existingConfig = xrayConfigService.get(serverId);
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
        // useTls=false 时 domain 应落库为 null; useTls=true 时落 reqVO.domain
        String newDomain = Boolean.TRUE.equals(reqVO.getUseTls()) ? reqVO.getDomain() : null;
        if (!ObjectUtil.equal(existingConfig.getDomain(), newDomain)) {
            mismatches.add("domain: " + existingConfig.getDomain() + " → " + newDomain);
        }
        if (CollUtil.isNotEmpty(mismatches)) {
            throw new BusinessException(XrayErrorCode.NODE_PARAM_CHANGE_BLOCKED,
                    serverId, activeCount, String.join("; ", mismatches));
        }
    }
}
