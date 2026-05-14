package com.nook.framework.ssh.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * SSH 会话基础参数 (yaml 前缀 nook.ssh).
 *
 * <p>默认值已按"跨国全球互联"场景调优 (高延迟链路 + 多重 NAT + 云防火墙):
 * 频繁心跳防 NAT 老化, TCP keepalive 双保险, 鉴权与建链超时拆开.
 * 局域网 / 内网场景下可以适当放大 heartbeat-interval 降低开销.
 *
 * @author nook
 */
@Data
@ConfigurationProperties(prefix = "nook.ssh")
public class SshSessionProperties {

    /**
     * TCP 建链 + SSH 握手 (KEX 密钥交换) 总超时.
     * 跨国链路慢启动 + 海外服务器 KEX 计算偶尔卡顿, 留足 90s.
     */
    private Duration connectTimeout = Duration.ofSeconds(90);

    /**
     * SSH 鉴权阶段独立超时 (密码 / key 验证); 比 connectTimeout 短才能早暴露错密码.
     * 密码校验是本地操作, > 20s 几乎一定是远端 sshd 有问题.
     */
    private Duration authTimeout = Duration.ofSeconds(20);

    /**
     * 心跳发送间隔 (应用层); 防 NAT/防火墙 idle 老化与远端 sshd 主动断连.
     * 跨国链路推荐 ≤ 15s, 内网可放宽到 60s 降低开销.
     */
    private Duration heartbeatInterval = Duration.ofSeconds(15);

    /**
     * 心跳回包等待; 超过则视为链路死, MINA 断开 session 触发 manager 摘出.
     * 设 heartbeatInterval × 2, 单次丢包不至于误判断链, 但持续不通 30s 内必摘.
     */
    private Duration heartbeatReplyWait = Duration.ofSeconds(30);

    /**
     * TCP keepalive (内核层 SO_KEEPALIVE); 应用层 heartbeat 失效时的双保险.
     * 全球互联强烈建议开; 心跳停发时, 内核 keepalive 仍能让远端断链感知到.
     */
    private boolean tcpKeepAlive = true;

    /**
     * TCP_NODELAY (关 Nagle 算法); SSH 是交互式协议, 关 Nagle 降低单条命令延迟.
     * 默认即应该 true; SSH 数据量小, Nagle 攒包反而增加 200ms+ 延迟.
     */
    private boolean tcpNoDelay = true;

    /**
     * 会话空闲超时 (MINA IDLE_TIMEOUT); 0 = 禁用 (仅靠 heartbeat 维持).
     *
     * <p>设 30 分钟, 让长时间不用的 session 自动释放, 减轻远端 sshd 连接表负担;
     * 业务侧下次 acquire 会自动重建. 不能太短, 防止 sample / reconcile 周期里被误关.
     */
    private Duration idleTimeout = Duration.ofMinutes(30);
}
