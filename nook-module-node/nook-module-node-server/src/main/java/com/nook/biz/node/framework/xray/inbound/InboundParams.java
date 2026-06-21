package com.nook.biz.node.framework.xray.inbound;

/**
 * Xray inbound 语义参数基类; 各协议参数子类的多态根, 按 protocol_key 还原到对应子类 (见 InboundParamsResolver)
 *
 * @author nook
 */
public abstract class InboundParams {

    /** admin 详情投影: WebSocket 接入路径 (vmess-ws); 非本形态留 null, 由对应协议子类 override. */
    public String getWsPath() {
        return null;
    }

    /** admin 详情投影: 对外域名 FQDN (vmess-tls); 非本形态留 null. */
    public String getDomain() {
        return null;
    }

    /** admin 详情投影: TLS 证书路径 (vmess-tls); 非本形态留 null. */
    public String getCertPath() {
        return null;
    }

    /** admin 详情投影: TLS 私钥路径 (vmess-tls); 非本形态留 null. */
    public String getKeyPath() {
        return null;
    }
}
