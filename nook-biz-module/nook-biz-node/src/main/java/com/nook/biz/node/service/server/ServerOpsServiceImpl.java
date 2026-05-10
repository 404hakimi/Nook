package com.nook.biz.node.service.server;

import cn.hutool.core.io.resource.ResourceUtil;
import com.nook.biz.node.controller.xray.server.vo.EnableSwapReqVO;
import com.nook.biz.node.framework.server.script.RemoteScriptRunner;
import com.nook.biz.node.framework.server.script.config.RemoteScriptPaths;
import com.nook.biz.node.framework.server.script.config.ServerOsOp;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.SshSessionManager;
import com.nook.biz.node.validator.ServerOpsValidator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
public class ServerOpsServiceImpl implements ServerOpsService {

    @Resource
    private SshSessionManager sessionManager;
    @Resource
    private RemoteScriptRunner scriptRunner;
    @Resource
    private ServerOpsValidator opsValidator;

    @Override
    public void enableSwap(String serverId, EnableSwapReqVO reqVO, Consumer<String> lineSink) {
        opsValidator.validateServerId(serverId);
        opsValidator.validateForEnableSwap(reqVO);
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("SWAP_SIZE_MB", String.valueOf(reqVO.getSizeMb()));
        runOsOp(serverId, ServerOsOp.SWAP, vars, lineSink);
    }

    @Override
    public void enableBbr(String serverId, Consumer<String> lineSink) {
        opsValidator.validateServerId(serverId);
        runOsOp(serverId, ServerOsOp.BBR, Map.of(), lineSink);
    }

    /** 拼"helpers + 单个 module"成完整脚本, 通过 RemoteScriptRunner 流式跑; 超时复用 install 上限 (op 量级相当). */
    private void runOsOp(String serverId,
                         ServerOsOp op,
                         Map<String, String> vars,
                         Consumer<String> lineSink) {
        SshSession session = sessionManager.acquire(serverId);
        String helpers = ResourceUtil.readUtf8Str(RemoteScriptPaths.OPS_HELPERS);
        String body = scriptRunner.renderTemplate(op.modulePath(), vars);
        String script = helpers + "\n" + body + "\n";
        log.info("[server-ops] {} server={} script-bytes={}", op.key(), serverId, script.length());
        scriptRunner.runScriptStreaming(
                session,
                script,
                RemoteScriptPaths.OPS_TMP + "-" + op.key(),
                Duration.ofSeconds(session.cred().getInstallTimeoutSeconds()),
                lineSink);
    }
}
