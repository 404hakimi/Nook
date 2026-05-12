package com.nook.biz.node.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 服务器运维参数 (yaml 前缀 nook.node.server)
 *
 * @author nook
 */
@Data
@ConfigurationProperties(prefix = "nook.node.server")
public class ServerOpsProperties {

    // 主机探活 SSH 探针超时; 短超时让"不通"快速返回, 跟跑业务命令的 op-timeout 区分
    private Duration probeTimeout = Duration.ofSeconds(10);

    // 远端脚本兜底清理 (rm -f) 的超时; 网络慢时给点余量
    private Duration scriptCleanupTimeout = Duration.ofSeconds(20);
}
