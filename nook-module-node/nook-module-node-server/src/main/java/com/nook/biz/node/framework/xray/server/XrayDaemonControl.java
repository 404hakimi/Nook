package com.nook.biz.node.framework.xray.server;

import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.framework.ssh.core.SshSession;
import org.springframework.stereotype.Component;

/**
 * Xray 守护进程 systemctl 操作: 重启 / 开机自启切换; 由 caller 传入已 acquire 的 session
 *
 * @author nook
 */
@Component
public class XrayDaemonControl {

    /**
     * 重启 xray.service 并回拉"是否 active + 版本"做前端展示; 一条复合命令完成 restart + sleep + 探活.
     * client 连接会断 1-2 秒重连.
     *
     * @param session caller 已 acquire 的 SSH 会话
     * @param xrayBin xray 二进制路径
     * @return 远端 stdout (含 active 状态 + xray version 首行)
     */
    public String restart(SshSession session, String xrayBin) {
        return session.ssh().exec(
                "systemctl restart " + XrayConstants.SYSTEMD_UNIT
                        + " && sleep 2 && systemctl is-active " + XrayConstants.SYSTEMD_UNIT
                        + " && " + xrayBin + " version | head -1"
        ).getStdout();
    }
}
