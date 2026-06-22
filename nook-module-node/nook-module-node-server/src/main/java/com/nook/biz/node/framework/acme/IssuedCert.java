package com.nook.biz.node.framework.acme;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一次 ACME 签发的产物: 全链证书 PEM + 私钥 PEM + 到期时间.
 * <p>由 {@link LetsEncryptCertService#issue} 返回; 落 xray_install 持久化, 部署时下发给 agent 写盘.
 *
 * @author nook
 */
@Data
@Builder
public class IssuedCert {

    /** 证书全链 PEM (leaf + 中间证书), 直接喂 xray tlsSettings.certificateFile. */
    private String certPem;

    /** 证书私钥 PEM (PKCS#1/SEC1), 喂 xray tlsSettings.keyFile. */
    private String keyPem;

    /** 叶子证书 notAfter (本地时区); 续期任务据此判临期. */
    private LocalDateTime notAfter;
}
