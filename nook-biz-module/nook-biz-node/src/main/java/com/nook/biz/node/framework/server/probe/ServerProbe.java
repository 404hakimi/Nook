package com.nook.biz.node.framework.server.probe;

import jakarta.annotation.Resource;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.server.snapshot.ConnectivitySnapshot;
import com.nook.biz.node.framework.server.snapshot.HostInfoSnapshot;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.server.snapshot.SystemdStatusSnapshot;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.SshSessionManager;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务器只读探测: 主机信息 / systemd 状态 / journal 日志 / 探活, 仅返回 snapshot record.
 *
 * @author nook
 */
@Slf4j
@Component
public class ServerProbe {

    /** 探活专用短超时 */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(10);

    /** 日志默认行数 / 上限; 上限 5000 防止前端误传超大值把 journalctl 拖死. */
    private static final int DEFAULT_LOG_LINES = 100;
    private static final int MAX_LOG_LINES = 5000;

    /** systemd unit 合法字符 (字母/数字/._-@); 直接拼到 shell 前必须校验防注入. */
    private static final Pattern UNIT_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._@-]{1,128}$");

    @Resource
    private SshSessionManager sessionManager;

    /**
     * 主机可达性探活, 走 SSH 跑 'true' 验证网络 / 凭据 / 通道; 任何异常都包成 success=false 不抛.
     *
     * @param serverId resource_server.id
     * @return ConnectivitySnapshot
     */
    public ConnectivitySnapshot probeConnectivity(String serverId) {
        long start = System.currentTimeMillis();
        try {
            sessionManager.acquire(serverId).ssh().exec("true", PROBE_TIMEOUT);
            long elapsed = System.currentTimeMillis() - start;
            log.info("[probe] OK server={} elapsed={}ms", serverId, elapsed);
            return new ConnectivitySnapshot(true, elapsed, null);
        } catch (BusinessException be) {
            // 业务异常已是预期失败 (凭据不全/远端拒绝/超时), warn 级别足够; 不打 stacktrace
            log.warn("[probe] FAIL server={} code={} msg={} elapsed={}ms",
                    serverId, be.getCode(), be.getMessage(), System.currentTimeMillis() - start);
            return new ConnectivitySnapshot(false, 0L, be.getMessage());
        } catch (Exception e) {
            // 非预期异常 (NPE 等) 也给前端友好响应
            log.error("[probe] UNEXPECTED server={} elapsed={}ms",
                    serverId, System.currentTimeMillis() - start, e);
            return new ConnectivitySnapshot(false, 0L,
                    "探活异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 主机层信息 (hostname / kernel / 内存 / 磁盘 / 时区), 一条复合 shell 拿全; 单段失败回空段不中断后续段.
     *
     * @param serverId resource_server.id
     * @return HostInfoSnapshot
     */
    public HostInfoSnapshot readHostInfo(String serverId) {
        // 一条复合 shell 拿全; 单段失败 echo 空段, 不中断后续段
        String composite = String.join("\n",
                "echo '====[HOSTNAME]===='",
                "hostname 2>/dev/null || true",
                "echo '====[KERNEL]===='",
                "uname -srm 2>/dev/null || true",
                "echo '====[OS_RELEASE]===='",
                ". /etc/os-release 2>/dev/null && echo \"$PRETTY_NAME\" || true",
                "echo '====[SYS_UPTIME]===='",
                "uptime -p 2>/dev/null || true",
                "echo '====[LOADAVG]===='",
                "cut -d' ' -f1-3 /proc/loadavg 2>/dev/null || true",
                "echo '====[MEMORY]===='",
                "free -m 2>/dev/null | awk '/^Mem:/ {used=$3; total=$2; printf \"%.1fG / %.1fG (%.0f%%)\\n\", used/1024, total/1024, used*100/total}' || true",
                "echo '====[DISK]===='",
                "df -h / 2>/dev/null | awk 'NR==2 {printf \"%s / %s (%s)\\n\", $3, $2, $5}' || true",
                "echo '====[TIMEZONE]===='",
                "timedatectl show -p Timezone --value 2>/dev/null || date +%Z || true"
        );
        SshSession session = sessionManager.acquire(serverId);
        String out = session.ssh().exec(composite).getStdout();
        return new HostInfoSnapshot(
                section(out, "HOSTNAME").trim(),
                section(out, "KERNEL").trim(),
                section(out, "OS_RELEASE").trim(),
                section(out, "SYS_UPTIME").trim(),
                section(out, "LOADAVG").trim(),
                section(out, "MEMORY").trim(),
                section(out, "DISK").trim(),
                section(out, "TIMEZONE").trim());
    }

    /**
     * 指定 systemd unit 的运行状态 (active / 启动时间 / 开机自启); unit 走 UNIT_NAME_PATTERN 防 shell 注入.
     *
     * @param serverId resource_server.id
     * @param unit     systemd unit 名
     * @return SystemdStatusSnapshot
     */
    public SystemdStatusSnapshot readSystemdStatus(String serverId, String unit) {
        if (StrUtil.isBlank(unit) || !UNIT_NAME_PATTERN.matcher(unit).matches()) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    serverId, "非法 systemd unit 名: " + unit);
        }
        String composite = String.join("\n",
                "echo '====[ACTIVE]===='",
                "systemctl is-active " + unit + " 2>/dev/null || true",
                "echo '====[UPTIME]===='",
                "systemctl show " + unit + " -p ActiveEnterTimestamp --value 2>/dev/null || true",
                "echo '====[ENABLED]===='",
                // is-enabled 在 disabled/static/masked 时退出码非 0, 但 stdout 仍是状态字符串; || true 防 set -e
                "systemctl is-enabled " + unit + " 2>/dev/null || true"
        );
        SshSession session = sessionManager.acquire(serverId);
        String out = session.ssh().exec(composite).getStdout();
        return new SystemdStatusSnapshot(
                unit,
                section(out, "ACTIVE").trim(),
                section(out, "UPTIME").trim(),
                section(out, "ENABLED").trim());
    }

    /**
     * 指定 systemd unit 的 journalctl 日志, 按行数 + 级别过滤; unit 走 UNIT_NAME_PATTERN 防 shell 注入.
     *
     * @param serverId resource_server.id
     * @param unit     systemd unit 名
     * @param logLines 行数 (默认 100, 上限 5000)
     * @param logLevel 级别过滤 (all / warning / err)
     * @return JournalLogSnapshot
     */
    public JournalLogSnapshot readJournalLog(String serverId, String unit, Integer logLines, String logLevel) {
        if (StrUtil.isBlank(unit) || !UNIT_NAME_PATTERN.matcher(unit).matches()) {
            // 直接拼到 journalctl -u <unit>, 必须严格校验; 非法 unit 抛业务异常而非裸 shell 注入
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    serverId, "非法 systemd unit 名: " + unit);
        }
        int lines = (logLines == null || logLines <= 0)
                ? DEFAULT_LOG_LINES
                : Math.min(logLines, MAX_LOG_LINES);
        String level = normalizeLevel(logLevel);
        String priorityFlag = switch (level) {
            case "warning" -> "-p warning";
            case "err" -> "-p err";
            default -> "";
        };
        String cmd = "journalctl -u " + unit + " " + priorityFlag + " -n " + lines + " --no-pager 2>/dev/null || true";
        SshSession session = sessionManager.acquire(serverId);
        String out = session.ssh().exec(cmd).getStdout();
        return new JournalLogSnapshot(unit, lines, level, out);
    }

    /**
     * 归一化前端传入的 level, 容错 warn / error 等同义词, 未识别一律 all.
     *
     * @param raw 入参 level
     * @return 归一化后的 level
     */
    private static String normalizeLevel(String raw) {
        if (StrUtil.isBlank(raw)) return "all";
        String lvl = raw.trim().toLowerCase();
        return switch (lvl) {
            case "warning", "warn" -> "warning";
            case "err", "error" -> "err";
            default -> "all";
        };
    }

    /**
     * 从形如 "====[NAME]====\n<内容>\n====[NEXT]====" 的输出里切一段.
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
