package com.nook.biz.node.framework.xray.inbound.strategy;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.api.xray.XrayInstallDefaults;
import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.framework.xray.inbound.config.InboundParams;
import com.nook.biz.node.service.xray.server.XrayInstallService;
import com.nook.biz.system.api.domain.SystemDomainApi;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * vmess + WebSocket 协议策略; 绑域名走 tls, 不绑走 none
 *
 * @author nook
 */
@Component
public class VmessWsStrategy implements InboundProtocolStrategy {

    @Resource
    private SystemDomainApi systemDomainApi;
    @Resource
    private XrayInstallService xrayInstallService;

    @Override
    public boolean supports(XrayInstallReqVO reqVO) {
        return "vmess".equalsIgnoreCase(reqVO.getProtocol());
    }

    @Override
    public void validate(String serverId, XrayInstallReqVO reqVO) {
        // 未绑域名 = 纯 ws, 无需域名/证书校验
        if (StrUtil.isBlank(reqVO.getDomainId())) {
            return;
        }
        systemDomainApi.getById(reqVO.getDomainId());
        if (StrUtil.isBlank(reqVO.getSubdomain())) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID, "绑定域名时二级域名 (subdomain) 必填");
        }
        if (xrayInstallService.isSubdomainTaken(reqVO.getDomainId(), reqVO.getSubdomain().trim(), serverId)) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID,
                    "该根域下二级域名 '" + reqVO.getSubdomain().trim() + "' 已被其他线路机占用, 请换一个");
        }
    }

    @Override
    public InboundProvision provision(XrayInstallReqVO reqVO, String fullDomain) {
        boolean useTls = StrUtil.isNotBlank(fullDomain);
        XrayInboundProtocolEnum protocol = useTls
                ? XrayInboundProtocolEnum.VMESS_WS_TLS : XrayInboundProtocolEnum.VMESS_WS_PLAIN;
        // 语义参数: ws path + (绑域名时) tls 路径
        InboundParams params = new InboundParams();
        InboundParams.WsParams ws = new InboundParams.WsParams();
        ws.setPath(reqVO.getWsPath());
        params.setWs(ws);
        if (useTls) {
            InboundParams.TlsParams tls = new InboundParams.TlsParams();
            tls.setCertPath(XrayInstallDefaults.TLS_CERT_PATH);
            tls.setKeyPath(XrayInstallDefaults.TLS_KEY_PATH);
            params.setTls(tls);
        }
        // 模板占位符
        Map<String, Object> vars = new HashMap<>();
        vars.put("tag", XrayConstants.SHARED_INBOUND_TAG);
        vars.put("listenIp", reqVO.getListenIp());
        vars.put("port", reqVO.getSharedInboundPort());
        vars.put("ws.path", reqVO.getWsPath());
        if (useTls) {
            vars.put("domain", fullDomain);
            vars.put("tls.certPath", XrayInstallDefaults.TLS_CERT_PATH);
            vars.put("tls.keyPath", XrayInstallDefaults.TLS_KEY_PATH);
        }
        return new InboundProvision(protocol, params, vars);
    }
}
