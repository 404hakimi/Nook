package com.nook.biz.node.framework.server.session.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 远程会话管理器可调参数; yaml 前缀 nook.node.session. */
@ConfigurationProperties(prefix = "nook.node.session")
public class ServerSessionProperties {

    /** 应 ≥ config-sync 等周期任务最长间隔, 否则 session 反复重建. */
    private Duration idleTimeout = Duration.ofMinutes(30);

    private Duration healthCheckInterval = Duration.ofSeconds(60);

    /** 设 1 会因瞬时丢包频繁误杀, 至少 2 容忍偶发抖动. */
    private int healthFailThreshold = 2;

    /** 仅作用于"并发等待 CONNECTING 完成", 实际握手超时由 connectTimeout 控制. */
    private Duration acquireTimeout = Duration.ofSeconds(10);

    private Duration connectTimeout = Duration.ofSeconds(10);

    /** 防 NAT 老化与远端 sshd 主动断连. */
    private Duration heartbeatInterval = Duration.ofSeconds(30);

    private Duration heartbeatReplyWait = Duration.ofSeconds(10);

    private Duration cleanupInterval = Duration.ofMinutes(5);

    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Duration idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public Duration getHealthCheckInterval() {
        return healthCheckInterval;
    }

    public void setHealthCheckInterval(Duration healthCheckInterval) {
        this.healthCheckInterval = healthCheckInterval;
    }

    public int getHealthFailThreshold() {
        return healthFailThreshold;
    }

    public void setHealthFailThreshold(int healthFailThreshold) {
        this.healthFailThreshold = healthFailThreshold;
    }

    public Duration getAcquireTimeout() {
        return acquireTimeout;
    }

    public void setAcquireTimeout(Duration acquireTimeout) {
        this.acquireTimeout = acquireTimeout;
    }

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

    public Duration getCleanupInterval() {
        return cleanupInterval;
    }

    public void setCleanupInterval(Duration cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
    }
}
