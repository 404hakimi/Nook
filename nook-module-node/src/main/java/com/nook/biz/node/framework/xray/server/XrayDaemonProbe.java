package com.nook.biz.node.framework.xray.server;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.framework.ssh.core.SshSession;
import com.nook.biz.node.framework.xray.XrayConstants;
import com.nook.biz.node.framework.xray.server.snapshot.XrayDaemonExtraSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Xray 进程级 SSH probe: 取二进制版本 + 监听端口段; 由 caller 传入已 acquire 的 session, apiPort 由调用方传入.
 *
 * @author nook
 */
@Slf4j
@Component
    public class XrayDaemonProbe {

    /**
     * 一次 SSH 复合命令拿 xray version + 监听端口段; apiPort 用于过滤 ss -ltn 的 loopback 行.
     *
     * @param session caller 已 acquire 的 SSH 会话
     * @param apiPort xray 内置 api server 端口
     * @return XrayDaemonExtraSnapshot
     */
    public XrayDaemonExtraSnapshot readExtras(SshSession session, int apiPort) {
        String composite = String.join("\n",
                "echo '====[VERSION]===='",
                "xray version 2>/dev/null | head -1 || true",
                "echo '====[LISTEN]===='",
                "ss -ltn 2>/dev/null | grep -E '(127.0.0.1:" + apiPort + " |:443 |:2087 )' || true"
        );
        String out = session.ssh().exec(composite).getStdout();
        return new XrayDaemonExtraSnapshot(
                section(out, "VERSION").trim(),
                section(out, "LISTEN").trim());
    }

    /**
     * 重启 xray.service 并回拉"是否 active + 版本"做前端展示; 一条复合命令完成 restart + sleep + 探活.
     * client 连接会断 1-2 秒重连.
     *
     * @param session caller 已 acquire 的 SSH 会话
     * @return 远端 stdout (含 active 状态 + xray version 首行)
     */
    public String restart(SshSession session) {
        return session.ssh().exec(
                "systemctl restart " + XrayConstants.SYSTEMD_UNIT
                        + " && sleep 2 && systemctl is-active " + XrayConstants.SYSTEMD_UNIT
                        + " && xray version | head -1"
        ).getStdout();
    }

    /**
     * 切换 xray.service 开机自启; 已 enabled/disabled 也算正常 (退出非 0 用 || true 兜底).
     * 末尾跑 is-enabled 给前端确认最终状态.
     *
     * @param session caller 已 acquire 的 SSH 会话
     * @param enabled true=enable, false=disable
     * @return 远端 stdout (末行 is-enabled 结果)
     */
    public String setAutostart(SshSession session, boolean enabled) {
        String op = enabled ? "enable" : "disable";
        return session.ssh().exec(
                "systemctl " + op + " " + XrayConstants.SYSTEMD_UNIT + " 2>&1 || true; "
                        + "systemctl is-enabled " + XrayConstants.SYSTEMD_UNIT + " 2>/dev/null || true"
        ).getStdout();
    }

    /**
     * 解析"用户请求版本"为实际版本 tag: requested="latest" 时探远端 xray 真实版本统一规范成 "vX.Y.Z";
     * 其它请求或抓取失败原样返回 requested.
     *
     * @param session   caller 已 acquire 的 SSH 会话
     * @param requested 用户请求的版本字符串 ("latest" 或 "vX.Y.Z")
     * @return 实际版本 tag (例 "v26.3.27") 或原 requested
     */
    public String resolveActualVersion(SshSession session, String requested) {
        if (!"latest".equalsIgnoreCase(requested)) return requested;
        String raw;
        try {
            raw = session.ssh().exec("xray version 2>/dev/null | head -1 | awk '{print $2}' || true")
                    .getStdout().trim();
        } catch (RuntimeException e) {
            log.warn("[xray-probe] resolveActualVersion 失败 server={}: {}", session.serverId(), e.getMessage());
            return requested;
        }
        if (StrUtil.isBlank(raw)) return requested;
        // 防御性: 远端可能加 v 前缀也可能不加; 统一规范成 "vX.Y.Z"
        return raw.startsWith("v") ? raw : "v" + raw;
    }

    /**
     * 探 xray.service 当前 Active 启动时刻 (epoch 秒); 用于 reconciler 检测 xray 是否被重启过.
     * systemctl 拿不到 (服务停 / unit 不存在) 时返 empty, 调用方按"不可探测"处理跳过本轮.
     *
     * @param session caller 已 acquire 的 SSH 会话
     * @return Instant 包装的 xray 启动时刻; 不可探测时 empty
     */
    public Optional<Instant> readUptime(SshSession session) {
        // systemctl show 返回的 ActiveEnterTimestamp 格式为 "Sat 2026-05-10 22:33:11 CST",
        // date -d 解析成 epoch 秒; xray 没起或 unit 缺失 → date 失败 → echo 0 兜底.
        String cmd = "date -d \"$(systemctl show -p ActiveEnterTimestamp --value xray 2>/dev/null)\" +%s 2>/dev/null || echo 0";
        String stdout;
        try {
            stdout = session.ssh().exec(cmd).getStdout().trim();
        } catch (RuntimeException e) {
            log.warn("[xray-probe] readUptime 失败 server={}: {}", session.serverId(), e.getMessage());
            return Optional.empty();
        }
        if (StrUtil.isBlank(stdout) || !NumberUtil.isLong(stdout)) return Optional.empty();
        long epochSec = Long.parseLong(stdout);
        if (epochSec <= 0) return Optional.empty();
        return Optional.of(Instant.ofEpochSecond(epochSec));
    }

    /**
     * 从形如 "====[NAME]====\n<内容>\n====[NEXT]====" 的输出里切一段; 与 ServerProbe.section 同款.
     *
     * @param raw  原始 stdout
     * @param name 段名
     * @return 段内容
     */
    private static String section(String raw, String name) {
        Pattern p = Pattern.compile("====\\[" + name + "\\]====\\R(.*?)(?=\\R====\\[|\\z)", Pattern.DOTALL);
        Matcher m = p.matcher(raw);
        return m.find() ? m.group(1) : "";
    }
}
