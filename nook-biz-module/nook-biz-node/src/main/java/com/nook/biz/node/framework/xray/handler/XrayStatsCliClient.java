package com.nook.biz.node.framework.xray.handler;

import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.SshSessionManager;
import com.nook.biz.node.service.xray.node.XrayNodeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Xray Stats CLI 客户端 (走 SSH + `xray api stats`).
 *
 * <p>替代了原 gRPC StatsService 客户端. 走 CLI 的考虑见 docs/演进路线.md 阶段 1 架构反思.
 *
 * <p>命令风格:
 * <pre>
 * xray api stats --server=127.0.0.1:8080 --name "user&gt;&gt;&gt;email&gt;&gt;&gt;traffic&gt;&gt;&gt;uplink" --reset false
 *   2&gt;/dev/null | grep -oP 'value: \K[0-9]+' || echo 0
 * </pre>
 * 通过 grep 在远端把"value: 12345"转成纯数字, Java 端只解析 long, 容错 (没 stat 时返回 0).
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayStatsCliClient {

    /** 单条 SSH+CLI 命令默认超时. */
    private static final Duration OP_TIMEOUT = Duration.ofSeconds(15);

    private static final String UPLINK_FORMAT = "user>>>%s>>>traffic>>>uplink";
    private static final String DOWNLINK_FORMAT = "user>>>%s>>>traffic>>>downlink";

    @Resource
    private SshSessionManager sshSessionManager;
    @Resource
    private XrayNodeService xrayNodeService;

    /**
     * 读单个 stat 字节数; 不存在 / 出错返回 0 (用户从未产生流量时 Xray 不建 stat 槽).
     *
     * @param serverId resource_server.id
     * @param statName Xray stat key (如 user>>>email>>>traffic>>>uplink)
     * @param reset    读后是否清零
     * @return 字节数; 出错或未建 stat 返回 0
     */
    public long readStat(String serverId, String statName, boolean reset) {
        XrayNodeDO node = xrayNodeService.loadOrThrow(serverId);
        SshSession session = sshSessionManager.acquire(serverId);
        // 远端用 grep 把 protobuf text 输出 "value: 12345" 提成纯数字, || echo 0 兜底未找到 stat
        String cmd = "xray api stats --server=127.0.0.1:" + node.getXrayGrpcPort()
                + " --name " + escapeShellArg(statName)
                + " --reset " + (reset ? "true" : "false")
                + " 2>/dev/null | grep -oP 'value: \\K[0-9]+' || echo 0";
        String stdout;
        try {
            stdout = session.ssh().exec(cmd, OP_TIMEOUT).stdout().trim();
        } catch (RuntimeException e) {
            // SSH 抖动 / xray 没起 都视为 0, 不让 stats 失败影响业务
            log.warn("[xray-cli] readStat 失败 server={} stat={}: {}",
                    serverId, statName, e.getMessage());
            return 0L;
        }
        try {
            return Long.parseLong(stdout);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 一次拿用户上下行 + 可选清零, 拼成 UserTraffic.
     *
     * @param serverId resource_server.id
     * @param email    user email
     * @param reset    读后是否清零 (resetTraffic 用 true, getTraffic 用 false)
     * @return UserTraffic (上下行 + 配额 0 / 不限制)
     */
    public UserTraffic readUserTraffic(String serverId, String email, boolean reset) {
        long up = readStat(serverId, String.format(UPLINK_FORMAT, email), reset);
        long down = readStat(serverId, String.format(DOWNLINK_FORMAT, email), reset);
        // totalBytes / expiry / enabled 在 nook 模式下由业务侧维护, 远端不维护 (传 0 / true 占位)
        return new UserTraffic(email, up, down, 0L, 0L, true);
    }

    private static String escapeShellArg(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
