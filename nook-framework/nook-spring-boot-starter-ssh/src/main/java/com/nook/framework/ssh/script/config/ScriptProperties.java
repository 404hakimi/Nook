package com.nook.framework.ssh.script.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 脚本执行参数 (yaml 前缀 nook.ssh.script).
 *
 * @author nook
 */
@Data
@ConfigurationProperties(prefix = "nook.ssh.script")
public class ScriptProperties {

    /** 远端脚本兜底清理 (rm -f) 的超时; 网络慢时给点余量. */
    private Duration cleanupTimeout = Duration.ofSeconds(20);
}
