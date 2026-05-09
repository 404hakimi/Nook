package com.nook.biz.node.framework.ssh.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * SSH 会话基础参数 (yaml 前缀 nook.node.ssh); lazy + 被动失效模型, 无 healthCheck / idleCleanup 周期任务.
 *
 * @author nook
 */
@ConfigurationProperties(prefix = "nook.node.ssh")
public class SshSessionProperties {

    /** SSH 握手 + 鉴权超时. */
    private Duration connectTimeout = Duration.ofSeconds(20);

    /** MINA SshClient 心跳间隔; 防 NAT 老化与远端 sshd 主动断连. */
    private Duration heartbeatInterval = Duration.ofSeconds(30);

    /** 心跳回包等待; 超过则 MINA 自动断开 session, 触发 close 回调让 manager 摘出. */
    private Duration heartbeatReplyWait = Duration.ofSeconds(20);

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public Duration getHeartbeatReplyWait() {
        return heartbeatReplyWait;
    }

    public void setHeartbeatReplyWait(Duration heartbeatReplyWait) {
        this.heartbeatReplyWait = heartbeatReplyWait;
    }
}
