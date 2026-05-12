package com.nook.biz.node.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SOCKS5 落地节点参数 (yaml 前缀 nook.node.socks5)
 *
 * @author nook
 */
@Data
@ConfigurationProperties(prefix = "nook.node.socks5")
public class Socks5Properties {

    // ad-hoc 部署 reqVO.installTimeoutSeconds 为空时的兜底值; @Valid 拒空已挡正常路径
    private long defaultInstallTimeoutSeconds = 600L;
}
