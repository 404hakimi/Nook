package com.nook.framework.ssh.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * SSH 会话基础参数 (yaml 前缀 nook.ssh); lazy + 被动失效模型, 无 healthCheck / idleCleanup 周期任务.
 *
 * @author nook
 */
@Data
@ConfigurationProperties(prefix = "nook.ssh")
public class SshSessionProperties {

    /** SSH 握手 + 鉴权超时. */
    private Duration connectTimeout = Duration.ofSeconds(20);

    /** MINA SshClient 心跳间隔; 防 NAT 老化与远端 sshd 主动断连. */
    private Duration heartbeatInterval = Duration.ofSeconds(30);

    /** 心跳回包等待; 超过则 MINA 自动断开 session, 触发 close 回调让 manager 摘出. */
    private Duration heartbeatReplyWait = Duration.ofSeconds(20);
}
