package com.nook.biz.node.framework.xray.install;

import lombok.Builder;
import lombok.Data;

/**
 * 证书续期下发请求: 后台重签后只把新 cert/key 推给 agent 写盘 + reload xray (轻量, 不重走整个装机).
 *
 * @author nook
 */
@Data
@Builder
public class XrayCertPushRequest {

    /** 线路机 ID. */
    private String serverId;

    /** 对外域名 FQDN (仅日志/校验用). */
    private String domain;

    /** 新签发的全链证书 PEM. */
    private String tlsCertPem;

    /** 新签发的证书私钥 PEM. */
    private String tlsKeyPem;

    /** agent 写盘 + reload 的超时秒数 (操作很快, 给冗余). */
    private int timeoutSeconds;
}
