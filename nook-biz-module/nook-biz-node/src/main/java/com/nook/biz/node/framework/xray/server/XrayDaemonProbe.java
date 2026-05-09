package com.nook.biz.node.framework.xray.server;

import jakarta.annotation.Resource;
import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.node.framework.ssh.SshSessionManager;
import com.nook.biz.node.framework.xray.server.snapshot.XrayDaemonExtraSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Xray 进程级 SSH probe: 取二进制版本 + 监听端口列表, 依赖远端 grpc 端口凭据过滤 ss -ltn.
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayDaemonProbe {

    /** 单条快速命令超时. */
    private static final Duration QUICK_TIMEOUT = Duration.ofSeconds(30);

    @Resource
    private SshSessionManager sessionManager;
    @Resource
    private ResourceServerApi resourceServerApi;

    /**
     * 一次 SSH 复合命令拿 xray version + 监听端口段.
     *
     * @param serverId resource_server.id
     * @return XrayDaemonExtraSnapshot
     */
    public XrayDaemonExtraSnapshot readExtras(String serverId) {
        int grpcPort = resourceServerApi.loadCredential(serverId).xrayGrpcPort();
        String composite = String.join("\n",
                "echo '====[VERSION]===='",
                "xray version 2>/dev/null | head -1 || true",
                "echo '====[LISTEN]===='",
                "ss -ltn 2>/dev/null | grep -E '(127.0.0.1:" + grpcPort + " |:443 |:2087 )' || true"
        );
        String out = sessionManager.acquire(serverId).ssh().exec(composite, QUICK_TIMEOUT).stdout();
        return new XrayDaemonExtraSnapshot(
                section(out, "VERSION").trim(),
                section(out, "LISTEN").trim());
    }

    /** 从形如 "====[NAME]====\n<内容>\n====[NEXT]====" 的输出里切一段; 与 ServerProbe.section 同款. */
    private static String section(String raw, String name) {
        Pattern p = Pattern.compile("====\\[" + name + "\\]====\\R(.*?)(?=\\R====\\[|\\z)", Pattern.DOTALL);
        Matcher m = p.matcher(raw);
        return m.find() ? m.group(1) : "";
    }
}
