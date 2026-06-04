package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.resource.vo.EnableSwapReqVO;
import com.nook.biz.node.framework.server.probe.ServerProbe;
import com.nook.biz.node.framework.server.script.NookScripts;
import com.nook.biz.node.framework.server.script.config.ServerOsOp;
import com.nook.biz.node.framework.server.snapshot.ConnectivitySnapshot;
import com.nook.biz.node.framework.server.snapshot.HostInfoSnapshot;
import com.nook.biz.node.framework.server.snapshot.JournalLogSnapshot;
import com.nook.biz.node.framework.server.snapshot.SystemdStatusSnapshot;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.biz.node.service.resource.ResourceServerOpsService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.ssh.core.SshExecResult;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import com.nook.framework.ssh.script.RemoteScriptRunner;
import com.nook.framework.ssh.script.ScriptCatalog;
import com.nook.framework.web.StreamingEndpointSupport;
import com.nook.framework.web.WebStreamingProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 服务器运维 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class ResourceServerOpsServiceImpl implements ResourceServerOpsService {

    @Resource
    private RemoteScriptRunner remoteScriptRunner;
    @Resource
    private ScriptCatalog scriptCatalog;
    @Resource
    private ServerProbe serverProbe;
    @Resource
    private ResourceServerValidator resourceServerValidator;
    @Resource
    private ResourceServerCredentialService resourceServerCredentialService;
    @Resource
    private StreamingEndpointSupport streamingEndpointSupport;
    @Resource
    private WebStreamingProperties webStreamingProperties;

    @Override
    public ResponseBodyEmitter enableSwapStream(String serverId, EnableSwapReqVO reqVO) {
        return streamingEndpointSupport.stream("ops-swap:" + serverId, opsEmitterTimeout(serverId),
                lineSink -> {
                    Map<String, String> vars = new LinkedHashMap<>();
                    vars.put("SWAP_SIZE_MB", String.valueOf(reqVO.getSizeMb()));
                    this.runOsOp(serverId, ServerOsOp.SWAP, vars, lineSink);
                });
    }

    @Override
    public ResponseBodyEmitter enableBbrStream(String serverId) {
        return streamingEndpointSupport.stream("ops-bbr:" + serverId, opsEmitterTimeout(serverId),
                lineSink -> runOsOp(serverId, ServerOsOp.BBR, Map.of(), lineSink));
    }

    @Override
    public ConnectivitySnapshot testConnectivity(String serverId) {
        SshSession session;
        try {
            session = SshSessions.acquire(serverId, SshSessionScope.SHARED);
        } catch (BusinessException be) {
            // acquire 阶段抛错 (无凭据 / 网络断 / 鉴权失败) 转结构化失败
            return new ConnectivitySnapshot(false, 0L, be.getMessage());
        }
        return serverProbe.probeConnectivity(session);
    }

    @Override
    public HostInfoSnapshot getSystemInfo(String serverId) {
        return serverProbe.readHostInfo(SshSessions.acquire(serverId, SshSessionScope.SHARED));
    }

    @Override
    public String getUfwStatus(String serverId) {
        return serverProbe.readUfwStatus(SshSessions.acquire(serverId, SshSessionScope.SHARED));
    }

    @Override
    public SystemdStatusSnapshot getSystemdStatus(String serverId, String unit) {
        return serverProbe.readSystemdStatus(
                SshSessions.acquire(serverId, SshSessionScope.SHARED), unit);
    }

    @Override
    public JournalLogSnapshot getServiceLog(String serverId, String unit,
                                            Integer lines, String level, String keyword) {
        return serverProbe.readJournalLog(
                SshSessions.acquire(serverId, SshSessionScope.SHARED), unit, lines, level, keyword);
    }

    @Override
    public List<String> listNetworkInterfaces(String serverId) {
        try {
            SshSession session = SshSessions.acquire(serverId, SshSessionScope.SHARED);
            // -o 每行一个; 第二字段是 iface 名; 排除 loopback
            SshExecResult result = session.ssh().exec(
                    "ip -o link show | awk -F': ' '{print $2}' | grep -v '^lo$'",
                    Duration.ofSeconds(10));
            if (result.getExitCode() != 0) {
                log.warn("[listNetworkInterfaces] serverId={} exit={} stderr={}",
                        serverId, result.getExitCode(), result.getStderr());
                return List.of();
            }
            return Arrays.stream(result.getStdout().split("\\R"))
                    .map(String::trim)
                    .filter(StrUtil::isNotEmpty)
                    .toList();
        } catch (BusinessException be) {
            log.warn("[listNetworkInterfaces] serverId={} SSH 失败: {}", serverId, be.getMessage());
            return List.of();
        }
    }

    /** emitter 整体窗口 = 业务超时 (credential.installTimeoutSeconds) + framework buffer. */
    private Duration opsEmitterTimeout(String serverId) {
        resourceServerValidator.validateExists(serverId);
        int installTimeout = resourceServerCredentialService.requireByServerId(serverId).getInstallTimeoutSeconds();
        return Duration.ofSeconds(installTimeout).plus(webStreamingProperties.getEmitterBuffer());
    }

    /** 拼 _helpers + 单个 module 成完整脚本流式跑. */
    private void runOsOp(String serverId, ServerOsOp op,
                         Map<String, String> vars, Consumer<String> lineSink) {
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.INSTALL);
        String helpers = ResourceUtil.readUtf8Str(NookScripts.OPS_HELPERS);
        String body = scriptCatalog.render(op.module(), vars);
        String script = helpers + "\n" + body + "\n";
        log.info("[runOsOp] op={} serverId={} bytes={}", op.key(), serverId, script.length());
        remoteScriptRunner.runScriptStreaming(
                session, script,
                NookScripts.OPS_TMP_PREFIX + "-" + op.key(),
                Duration.ofSeconds(session.cred().getInstallTimeoutSeconds()),
                lineSink);
    }
}
