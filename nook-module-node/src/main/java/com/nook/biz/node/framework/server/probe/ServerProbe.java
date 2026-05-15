package com.nook.biz.node.framework.server.probe;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.config.ServerOpsProperties;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.server.snapshot.ConnectivitySnapshot;
import com.nook.biz.node.framework.server.snapshot.HostInfoSnapshot;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.server.snapshot.SystemdStatusSnapshot;
import com.nook.framework.ssh.core.SshSession;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务器只读探测: 主机信息 / systemd 状态 / journal 日志 / 探活, 仅返回 snapshot record.
 * 由 caller 传入已 acquire 的 session, framework 不知道凭据从哪来.
 *
 * @author nook
 */
@Slf4j
@Component
public class ServerProbe {

    /** 日志默认行数 / 上限; 上限 5000 防止前端误传超大值把 journalctl 拖死. */
    private static final int DEFAULT_LOG_LINES = 100;
    private static final int MAX_LOG_LINES = 5000;

    /** systemd unit 合法字符 (字母/数字/._-@); 直接拼到 shell 前必须校验防注入. */
    private static final Pattern UNIT_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._@-]{1,128}$");

    /**
     * 日志关键词允许字符集: Unicode 字母/数字 + 空格 + 常见无害标点; 严格白名单防 shell 注入.
     * 禁: '"`\$|&;<>(){}*?[]!#~=^ 等. 够覆盖 IP / email / port / 文件路径 / 类名等典型搜索词.
     */
    private static final Pattern LOG_KEYWORD_PATTERN = Pattern.compile("^[\\p{L}\\p{N} ._\\-:/@]{1,128}$");

    @Resource
    private ServerOpsProperties serverOpsProperties;

    /**
     * 在已 acquire 的 session 上跑 'true' 验证 shell 通道; 与"无法连"区分由业务侧捕获 acquire 异常.
     *
     * @param session caller 已 acquire 的 SSH 会话
     * @return ConnectivitySnapshot
     */
    public ConnectivitySnapshot probeConnectivity(SshSession session) {
        long start = System.currentTimeMillis();
        try {
            session.ssh().exec("true", serverOpsProperties.getProbeTimeout());
            long elapsed = System.currentTimeMillis() - start;
            log.info("[probe] OK server={} elapsed={}ms", session.serverId(), elapsed);
            return new ConnectivitySnapshot(true, elapsed, null);
        } catch (BusinessException be) {
            log.warn("[probe] FAIL server={} code={} msg={} elapsed={}ms",
                    session.serverId(), be.getCode(), be.getMessage(), System.currentTimeMillis() - start);
            return new ConnectivitySnapshot(false, 0L, be.getMessage());
        } catch (Exception e) {
            log.error("[probe] UNEXPECTED server={} elapsed={}ms",
                    session.serverId(), System.currentTimeMillis() - start, e);
            return new ConnectivitySnapshot(false, 0L,
                    "探活异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 主机层信息 (hostname / kernel / 内存 / 磁盘 / 时区), 一条复合 shell 拿全; 单段失败回空段不中断后续段.
     *
     * @param session caller 已 acquire 的 SSH 会话
     * @return HostInfoSnapshot
     */
    public HostInfoSnapshot readHostInfo(SshSession session) {
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
     * @param session caller 已 acquire 的 SSH 会话
     * @param unit    systemd unit 名
     * @return SystemdStatusSnapshot
     */
    public SystemdStatusSnapshot readSystemdStatus(SshSession session, String unit) {
        if (StrUtil.isBlank(unit) || !UNIT_NAME_PATTERN.matcher(unit).matches()) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    session.serverId(), "非法 systemd unit 名: " + unit);
        }
        String composite = String.join("\n",
                "echo '====[ACTIVE]===='",
                "systemctl is-active " + unit + " 2>/dev/null || true",
                "echo '====[UPTIME]===='",
                // systemctl 原文形如 "Thu 2026-05-14 22:32:47 CST"; CST 缩写有歧义 (国标 vs 美标)
                // 用 date -d 重格式化为 "2026-05-14 22:32:47 +0800" 这种 ISO-like + 数字时区; 失败兜底原文
                "v=$(systemctl show " + unit + " -p ActiveEnterTimestamp --value 2>/dev/null);"
                        + "[ -n \"$v\" ] && (date -d \"$v\" '+%Y-%m-%d %H:%M:%S %z' 2>/dev/null || echo \"$v\")",
                "echo '====[ENABLED]===='",
                // is-enabled 在 disabled/static/masked 时退出码非 0, 但 stdout 仍是状态字符串; || true 防 set -e
                "systemctl is-enabled " + unit + " 2>/dev/null || true"
        );
        String out = session.ssh().exec(composite).getStdout();
        return new SystemdStatusSnapshot(
                unit,
                section(out, "ACTIVE").trim(),
                section(out, "UPTIME").trim(),
                section(out, "ENABLED").trim());
    }

   /**
     * 指定 systemd unit 的 journalctl 日志, 按行数 + 级别 + 关键词过滤.
     * 注意: lines 是 journalctl 拉的原始末尾行数, keyword 在这些行里再做子串过滤;
     * 不会"再拉到凑够 lines 行命中"。需要更多上下文请加大 lines.
     *
     * @param session  caller 已 acquire 的 SSH 会话
     * @param unit     systemd unit 名 (走 UNIT_NAME_PATTERN 防注入)
     * @param logLines 行数 (默认 100, 上限 5000)
     * @param logLevel 级别过滤 (all / warning / err)
     * @param keyword  关键词子串过滤 (大小写不敏感); 空 / null 表示不过滤; 走 LOG_KEYWORD_PATTERN 防注入
     * @return JournalLogSnapshot
     */
    public JournalLogSnapshot readJournalLog(SshSession session, String unit,
                                              Integer logLines, String logLevel, String keyword) {
        if (StrUtil.isBlank(unit) || !UNIT_NAME_PATTERN.matcher(unit).matches()) {
            // 直接拼到 journalctl -u <unit>, 必须严格校验; 非法 unit 抛业务异常而非裸 shell 注入
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    session.serverId(), "非法 systemd unit 名: " + unit);
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
        // keyword: 字符白名单校验 + 单引号包裹; 非法直接拒绝, 不静默忽略以免误以为搜了空字符串
        String normalizedKeyword = StrUtil.isBlank(keyword) ? null : keyword.trim();
        String grepClause = "";
        if (normalizedKeyword != null) {
            if (!LOG_KEYWORD_PATTERN.matcher(normalizedKeyword).matches()) {
                throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                        session.serverId(), "非法搜索关键词 (仅允许字母/数字/中文/空格/._-:/@): " + keyword);
            }
            // grep -F: 固定字符串 (不当正则解析); -i: case-insensitive; --color=never: 防 ANSI 色码污染输出
            // 单引号包裹 keyword; LOG_KEYWORD_PATTERN 已排除单引号, 不会闭合
            grepClause = " | grep -i -F --color=never -- '" + normalizedKeyword + "'";
        }
        String cmd = "journalctl -u " + unit + " " + priorityFlag + " -n " + lines + " --no-pager"
                + grepClause + " 2>/dev/null || true";
        String out = session.ssh().exec(cmd).getStdout();
        return new JournalLogSnapshot(unit, lines, level, normalizedKeyword, out);
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
