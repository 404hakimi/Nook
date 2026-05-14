package com.nook.biz.node.service.resource.impl;

import cn.hutool.core.io.resource.ResourceUtil;
import com.nook.biz.node.controller.resource.vo.EnableSwapReqVO;
import com.nook.biz.node.framework.server.script.RemoteScriptRunner;
import com.nook.biz.node.framework.server.script.config.RemoteScriptPaths;
import com.nook.biz.node.framework.server.script.config.ServerOsOp;
import com.nook.biz.node.service.resource.ResourceServerOpsService;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 服务器通用 OS 调优运维 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class ResourceServerOpsServiceImpl implements ResourceServerOpsService {

    @Resource
    private RemoteScriptRunner scriptRunner;

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

    /** 拼"helpers + 单个 module"成完整脚本, 通过 RemoteScriptRunner 流式跑; 超时复用 install 上限 (op 量级相当). */
    private void runOsOp(String serverId,
                         ServerOsOp op,
                         Map<String, String> vars,
                         Consumer<String> lineSink) {
        // bbr / swap 是流式部署长任务, 走 INSTALL scope 跟短任务隔离, 避免被并发 invalidate 打断
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.INSTALL);
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
