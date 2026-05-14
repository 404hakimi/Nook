package com.nook.biz.node.handler.xray.server;

import com.nook.biz.node.framework.xray.server.XrayDaemonProbe;
import com.nook.biz.node.service.xray.client.XrayClientTrafficSampleService;
import com.nook.biz.operation.api.ProgressSink;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Xray Server 操作执行体 (op queue handler 调本类干活).
 *
 * <p>从 XrayServerManageServiceImpl 拆出来; service 只留对 controller 的入队接口.
 * package-private 锁住跨包绕队列调用.
 *
 * @author nook
 */
@Slf4j
@Component
public class ServerOpExecutor {

    @Resource
    private XrayDaemonProbe xrayDaemonProbe;
    /** restart 前置 sample 让流量数据不丢; 失败仅 warn, 不阻塞 restart 主流程. */
    @Resource
    private XrayClientTrafficSampleService trafficSampleService;

    /** XRAY_RESTART 实际执行体. */
    String doRestart(String serverId, ProgressSink progress) {
        ProgressSink sink = progress == null ? ProgressSink.noop() : progress;
        // restart 是可控的"清零事件" — systemctl restart 后 xray in-memory counter 全归零;
        // 先 sample 一次把当前增量入库, 让用户层流量统计跨重启不丢. 失败仅 warn, 不阻塞 restart.
        sink.report("采样流量入库", 20);
        try {
            trafficSampleService.sampleServerTraffic(serverId);
        } catch (Exception e) {
            log.warn("[restart] server={} 前置 sample 失败, 仍继续 restart: {}", serverId, e.getMessage());
        }
        sink.report("获取 SSH 会话", 40);
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.SHARED);
        sink.report("正在执行 systemctl restart", 60);
        String out = xrayDaemonProbe.restart(session);
        sink.report("等待进程就绪", 90);
        return out;
    }

    /** SERVER_AUTOSTART 实际执行体. */
    String doSetAutostart(String serverId, boolean enabled, ProgressSink progress) {
        ProgressSink sink = progress == null ? ProgressSink.noop() : progress;
        sink.report("建立 SSH 会话", 50);
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.SHARED);
        sink.report(enabled ? "执行 systemctl enable" : "执行 systemctl disable", 80);
        return xrayDaemonProbe.setAutostart(session, enabled);
    }
}
