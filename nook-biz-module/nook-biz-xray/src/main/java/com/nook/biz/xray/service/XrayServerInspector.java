package com.nook.biz.xray.service;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.xray.constant.XrayConstants;
import com.nook.biz.xray.controller.server.vo.ServerSystemInfoRespVO;
import com.nook.biz.xray.controller.server.vo.XrayLogRespVO;
import com.nook.biz.xray.controller.server.vo.XrayServiceStatusRespVO;
import com.nook.biz.xray.util.SshExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务器只读检视: 系统信息 / Xray 服务状态 / Xray 日志, 三类各一个 SSH 命令组,
 * 单独暴露给运维台对应的三个独立接口 (一次只取自己关心的那块, 减小响应体)。
 *
 * <p>与 {@link ServerProvisioner} 区分: provisioner 负责 install / restart 等"写"操作, 这里仅"读"。
 */
@Service
@RequiredArgsConstructor
public class XrayServerInspector {

    /** 单次 SSH 命令超时上限; 这些只读命令通常 1-3s, 给 30s 兜底 journalctl 慢盘场景。 */
    private static final int OP_TIMEOUT_SECONDS = 30;

    /** 日志默认行数 / 上限; 上限 5000 防止前端误传超大值把 journalctl 拖死。 */
    private static final int DEFAULT_LOG_LINES = 100;
    private static final int MAX_LOG_LINES = 5000;

    private final ResourceServerApi resourceServerApi;
    private final SshExecutor sshExecutor;

    /** 系统基本信息 (hostname / kernel / 内存 / 磁盘 / 时区 等)。 */
    public ServerSystemInfoRespVO getSystemInfo(String serverId) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        // 一条复合 shell 拿全; 单段命令任一失败 echo 空段, 不中断后续段
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
                // 输出形如 "2.1G / 7.7G (28%)"
                "free -m 2>/dev/null | awk '/^Mem:/ {used=$3; total=$2; printf \"%.1fG / %.1fG (%.0f%%)\\n\", used/1024, total/1024, used*100/total}' || true",
                "echo '====[DISK]===='",
                "df -h / 2>/dev/null | awk 'NR==2 {printf \"%s / %s (%s)\\n\", $3, $2, $5}' || true",
                "echo '====[TIMEZONE]===='",
                "timedatectl show -p Timezone --value 2>/dev/null || date +%Z || true"
        );
        String out = sshExecutor.exec(cred, composite, OP_TIMEOUT_SECONDS);
        ServerSystemInfoRespVO vo = new ServerSystemInfoRespVO();
        vo.setHostname(section(out, "HOSTNAME").trim());
        vo.setKernel(section(out, "KERNEL").trim());
        vo.setOsRelease(section(out, "OS_RELEASE").trim());
        vo.setSystemUptime(section(out, "SYS_UPTIME").trim());
        vo.setLoadAvg(section(out, "LOADAVG").trim());
        vo.setMemory(section(out, "MEMORY").trim());
        vo.setDisk(section(out, "DISK").trim());
        vo.setTimezone(section(out, "TIMEZONE").trim());
        return vo;
    }

    /** Xray systemd 服务状态 (active / version / 启动时间 / 监听端口)。 */
    public XrayServiceStatusRespVO getServiceStatus(String serverId) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        String unit = XrayConstants.SYSTEMD_UNIT;
        String composite = String.join("\n",
                "echo '====[ACTIVE]===='",
                "systemctl is-active " + unit + " 2>/dev/null || true",
                "echo '====[VERSION]===='",
                "xray version 2>/dev/null | head -1 || true",
                "echo '====[UPTIME]===='",
                "systemctl show " + unit + " -p ActiveEnterTimestamp --value 2>/dev/null || true",
                "echo '====[LISTEN]===='",
                "ss -ltn 2>/dev/null | grep -E '(127.0.0.1:" + cred.xrayGrpcPort() + " |:443 |:2087 )' || true"
        );
        String out = sshExecutor.exec(cred, composite, OP_TIMEOUT_SECONDS);
        XrayServiceStatusRespVO vo = new XrayServiceStatusRespVO();
        vo.setActive(section(out, "ACTIVE").trim());
        vo.setVersion(section(out, "VERSION").trim());
        vo.setUptimeFrom(section(out, "UPTIME").trim());
        vo.setListening(section(out, "LISTEN").trim());
        return vo;
    }

    /**
     * Xray journalctl 日志, 按行数 + 级别过滤。
     *
     * @param logLines null/<=0 走 {@link #DEFAULT_LOG_LINES}, 上限 {@link #MAX_LOG_LINES}
     * @param logLevel null/all 不过滤; warning/warn → -p warning; err/error → -p err
     */
    public XrayLogRespVO getLog(String serverId, Integer logLines, String logLevel) {
        ServerCredentialDTO cred = resourceServerApi.loadCredential(serverId);
        int lines = (logLines == null || logLines <= 0)
                ? DEFAULT_LOG_LINES
                : Math.min(logLines, MAX_LOG_LINES);
        String level = normalizeLevel(logLevel);
        String priorityFlag = switch (level) {
            case "warning" -> "-p warning";
            case "err" -> "-p err";
            default -> ""; // all
        };
        String unit = XrayConstants.SYSTEMD_UNIT;
        String cmd = "journalctl -u " + unit + " " + priorityFlag + " -n " + lines + " --no-pager 2>/dev/null || true";
        String out = sshExecutor.exec(cred, cmd, OP_TIMEOUT_SECONDS);
        XrayLogRespVO vo = new XrayLogRespVO();
        vo.setLines(lines);
        vo.setLevel(level);
        vo.setLog(out);
        return vo;
    }

    private static String normalizeLevel(String raw) {
        if (StrUtil.isBlank(raw)) return "all";
        String lvl = raw.trim().toLowerCase();
        return switch (lvl) {
            case "warning", "warn" -> "warning";
            case "err", "error" -> "err";
            default -> "all";
        };
    }

    /** 从形如 "====[NAME]====\n<内容>\n====[NEXT]====" 的输出里切一段。 */
    private static String section(String raw, String name) {
        Pattern p = Pattern.compile("====\\[" + name + "\\]====\\R(.*?)(?=\\R====\\[|\\z)", Pattern.DOTALL);
        Matcher m = p.matcher(raw);
        return m.find() ? m.group(1) : "";
    }
}
