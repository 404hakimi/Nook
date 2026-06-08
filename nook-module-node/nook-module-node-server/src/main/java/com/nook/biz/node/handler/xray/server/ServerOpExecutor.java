package com.nook.biz.node.handler.xray.server;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.dal.dataobject.node.XrayInstallDO;
import com.nook.biz.node.framework.xray.server.XrayDaemonProbe;
import com.nook.biz.node.validator.XrayInstallValidator;
import com.nook.biz.operation.api.OpProgressSink;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Xray 服务器操作执行体 (操作队列处理器调本类干活).
 *
 * <p>从 XrayInstallManageServiceImpl 拆出来; service 只留对 controller 的入队接口.
 * 包级私有, 防跨包绕过队列直接调用.
 *
 * <p>这里是 xray 守护进程级命令式操作 (重启 / 开机自启), 刻意保留后端 SSH 下发: 它们不是 xray
 * 配置/客户端数据, 无法用声明式对账表达 ("立即重启"是一次性命令而非稳定期望态);
 * 客户端数据操作 (开通/吊销/轮换) 才走仅写库 + agent 对账, 后端不直连 xray.
 *
 * @author nook
 */
@Slf4j
@Component
public class ServerOpExecutor {

    @Resource
    private XrayDaemonProbe xrayDaemonProbe;
    @Resource
    private XrayInstallValidator xrayInstallValidator;

    /** XRAY_RESTART 实际执行体. */
    String doRestart(String serverId, OpProgressSink progress) {
        OpProgressSink sink = ObjectUtil.isNull(progress) ? OpProgressSink.noop() : progress;
        XrayInstallDO server = xrayInstallValidator.validateExists(serverId);
        sink.report("连接服务器", 40);
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.SHARED);
        sink.report("重启 Xray 服务", 60);
        String out = xrayDaemonProbe.restart(session, server.getXrayBinaryPath());
        sink.report("等待进程就绪", 90);
        return out;
    }

    /** SERVER_AUTOSTART 实际执行体. */
    String doSetAutostart(String serverId, boolean enabled, OpProgressSink progress) {
        OpProgressSink sink = ObjectUtil.isNull(progress) ? OpProgressSink.noop() : progress;
        sink.report("连接服务器", 50);
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.SHARED);
        sink.report(enabled ? "开启开机自启" : "关闭开机自启", 80);
        return xrayDaemonProbe.setAutostart(session, enabled);
    }
}
