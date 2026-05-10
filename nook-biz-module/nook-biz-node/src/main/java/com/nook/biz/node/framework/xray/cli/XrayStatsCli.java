package com.nook.biz.node.framework.xray.cli;

import cn.hutool.core.util.NumberUtil;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.SshSessionManager;
import com.nook.biz.node.framework.xray.cli.snapshot.XrayUserTrafficSnapshot;
import com.nook.biz.node.framework.xray.cli.utils.ShellEscapeUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Xray Stats CLI 客户端 (走 SSH + xray api stats); 远端 grep 剥成纯数字, Java 端只解析 long.
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayStatsCli {

    private static final String UPLINK_FORMAT = "user>>>%s>>>traffic>>>uplink";
    private static final String DOWNLINK_FORMAT = "user>>>%s>>>traffic>>>downlink";

    @Resource
    private SshSessionManager sshSessionManager;

    /**
     * 读单个 stat 字节数; 不存在 / SSH 抖动 / xray 没起 都视为 0, 不让 stats 失败影响业务.
     *
     * @param serverId resource_server.id
     * @param apiPort  xray 内置 api server 端口
     * @param statName Xray stat key
     * @param reset    读后是否清零
     * @return stat 字节数 (不存在 / 出错均返 0)
     */
    public long readStat(String serverId, int apiPort, String statName, boolean reset) {
        SshSession session = sshSessionManager.acquire(serverId);
        // 远端用 grep 把 protobuf text 输出 "value: 12345" 提成纯数字, || echo 0 兜底未找到 stat
        String cmd = "xray api stats --server=127.0.0.1:" + apiPort
                + " --name " + ShellEscapeUtils.shellArg(statName)
                + " --reset " + (reset ? "true" : "false")
                + " 2>/dev/null | grep -oP 'value: \\K[0-9]+' || echo 0";
        String stdout;
        try {
            stdout = session.ssh().exec(cmd).getStdout().trim();
        } catch (RuntimeException e) {
            log.warn("[xray-cli] readStat 失败 server={} stat={}: {}",
                    serverId, statName, e.getMessage());
            return 0L;
        }
        // 远端 grep 已剥成纯数字, 但 SSH 抖动可能多带回车 / 空白; NumberUtil.isLong 兜底
        return NumberUtil.isLong(stdout) ? Long.parseLong(stdout) : 0L;
    }

    /**
     * 一次拿用户上下行 + 可选清零, 拼成 XrayUserTrafficSnapshot; reset=true 用于 resetTraffic.
     *
     * @param serverId resource_server.id
     * @param apiPort  xray 内置 api server 端口
     * @param email    user email
     * @param reset    读后是否清零
     * @return XrayUserTrafficSnapshot
     */
    public XrayUserTrafficSnapshot readUserTraffic(String serverId, int apiPort, String email, boolean reset) {
        long up = readStat(serverId, apiPort, String.format(UPLINK_FORMAT, email), reset);
        long down = readStat(serverId, apiPort, String.format(DOWNLINK_FORMAT, email), reset);
        // totalBytes / expiry / enabled 在 nook 模式下由业务侧维护, 远端不维护 (传 0 / true 占位)
        return new XrayUserTrafficSnapshot(email, up, down, 0L, 0L, true);
    }
}
