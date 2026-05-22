package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.io.resource.ResourceUtil;
import com.nook.biz.node.controller.resource.vo.EnableSwapReqVO;
import com.nook.biz.node.framework.server.script.NookScripts;
import com.nook.biz.node.framework.server.script.config.ServerOsOp;
import com.nook.biz.node.service.resource.ResourceServerOpsService;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import com.nook.framework.ssh.script.RemoteScriptRunner;
import com.nook.framework.ssh.script.ScriptCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 资源服务器运维 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceServerOpsServiceImpl implements ResourceServerOpsService {

    private final RemoteScriptRunner scriptRunner;
    private final ScriptCatalog scriptCatalog;

    @Override
    public void enableSwap(String serverId, EnableSwapReqVO reqVO, Consumer<String> lineSink) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("SWAP_SIZE_MB", String.valueOf(reqVO.getSizeMb()));
        runOsOp(serverId, ServerOsOp.SWAP, vars, lineSink);
    }

    @Override
    public void enableBbr(String serverId, Consumer<String> lineSink) {
        runOsOp(serverId, ServerOsOp.BBR, Map.of(), lineSink);
    }

    /** 拼"_helpers + 单个 module"成完整脚本流式跑; 超时复用 install 上限. */
    private void runOsOp(String serverId, ServerOsOp op,
                         Map<String, String> vars, Consumer<String> lineSink) {
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.INSTALL);
        String helpers = ResourceUtil.readUtf8Str(NookScripts.OPS_HELPERS);
        String body = scriptCatalog.render(op.module(), vars);
        String script = helpers + "\n" + body + "\n";
        log.info("[runOsOp] op={} serverId={} bytes={}", op.key(), serverId, script.length());
        scriptRunner.runScriptStreaming(
                session, script,
                NookScripts.OPS_TMP_PREFIX + "-" + op.key(),
                Duration.ofSeconds(session.cred().getInstallTimeoutSeconds()),
                lineSink);
    }
}
