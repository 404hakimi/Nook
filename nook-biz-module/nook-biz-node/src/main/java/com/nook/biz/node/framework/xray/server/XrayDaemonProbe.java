package com.nook.biz.node.framework.xray.server;

import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.SshSessionManager;
import com.nook.biz.node.framework.xray.server.snapshot.XrayDaemonExtraSnapshot;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Xray 进程级 SSH probe: 取二进制版本 + 监听端口段; 不查 DB, apiPort 由调用方传入.
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayDaemonProbe {

    @Resource
    private SshSessionManager sessionManager;

    /**
     * 一次 SSH 复合命令拿 xray version + 监听端口段; apiPort 用于过滤 ss -ltn 的 loopback 行.
     *
     * @param serverId resource_server.id
     * @param apiPort  xray 内置 api server 端口
     * @return XrayDaemonExtraSnapshot
     */
    public XrayDaemonExtraSnapshot readExtras(String serverId, int apiPort) {
        SshSession session = sessionManager.acquire(serverId);
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
