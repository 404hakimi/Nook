package com.nook.biz.node.handler.xray.server;

import com.nook.biz.node.dal.dataobject.node.XrayServerDO;
import com.nook.biz.node.framework.xray.server.XrayDaemonProbe;
import com.nook.biz.node.service.xray.client.XrayClientTrafficSampleService;
import com.nook.biz.node.validator.XrayServerValidator;
import com.nook.biz.operation.api.OpProgressSink;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ServerOpExecutor {

    private final XrayDaemonProbe xrayDaemonProbe;
    private final XrayServerValidator xrayServerValidator;
    /** restart 前置 sample 让流量数据不丢; 失败仅 warn, 不阻塞 restart 主流程. */
    private final XrayClientTrafficSampleService trafficSampleService;

    /** XRAY_RESTART 实际执行体. */
    String doRestart(String serverId, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        XrayServerDO server = xrayServerValidator.validateExists(serverId);
        // restart 是可控的"清零事件" — systemctl restart 后 xray in-memory counter 全归零;
        // 先 sample 一次把当前增量入库, 让用户层流量统计跨重启不丢. 失败仅 warn, 不阻塞 restart.
        sink.report("采样流量入库", 20);
        try {
            trafficSampleService.sampleServerTraffic(serverId);
        } catch (Exception e) {
            log.warn("[restart] server={} 前置 sample 失败, 仍继续 restart: {}", serverId, e.getMessage());
        }
        sink.report("连接服务器", 40);
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.SHARED);
        sink.report("重启 Xray 服务", 60);
        String out = xrayDaemonProbe.restart(session, server.getXrayBinaryPath());
        sink.report("等待进程就绪", 90);
        return out;
    }

    /** SERVER_AUTOSTART 实际执行体. */
    String doSetAutostart(String serverId, boolean enabled, OpProgressSink progress) {
        OpProgressSink sink = progress == null ? OpProgressSink.noop() : progress;
        sink.report("连接服务器", 50);
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.SHARED);
        sink.report(enabled ? "开启开机自启" : "关闭开机自启", 80);
        return xrayDaemonProbe.setAutostart(session, enabled);
    }
}
