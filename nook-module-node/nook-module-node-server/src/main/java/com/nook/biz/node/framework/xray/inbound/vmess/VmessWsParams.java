package com.nook.biz.node.framework.xray.inbound.vmess;

import com.nook.biz.node.framework.xray.inbound.InboundParams;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * vmess + WebSocket 语义参数; ws 接入路径 + (绑域名时) tls 文件证书/域名
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class VmessWsParams extends InboundParams {

    /** WebSocket 传输参数. */
    private WsParams ws;

    /** TLS 安全层参数; 绑域名时有值, 纯 ws 留空. */
    private TlsParams tls;

    @Override
    public String getWsPath() {
        return ws == null ? null : ws.getPath();
    }

    @Override
    public String getDomain() {
        return tls == null ? null : tls.getDomain();
    }

    @Override
    public String getCertPath() {
        return tls == null ? null : tls.getCertPath();
    }

    @Override
    public String getKeyPath() {
        return tls == null ? null : tls.getKeyPath();
    }

    /**
     * WebSocket 传输参数
     *
     * @author nook
     */
    @Data
    public static class WsParams {

        /** WebSocket 接入路径. */
        private String path;
    }

    /**
     * TLS 安全层参数 (文件证书)
     *
     * @author nook
     */
    @Data
    public static class TlsParams {

        /** TLS 证书路径. */
        private String certPath;

        /** TLS 私钥路径. */
        private String keyPath;

        /** 对外域名 (FQDN); 客户端 host + SNI, 订阅取此 (替代旧 xray_inbound.domain 列). */
        private String domain;
    }
}
