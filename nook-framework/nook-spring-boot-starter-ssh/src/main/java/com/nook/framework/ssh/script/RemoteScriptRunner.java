package com.nook.framework.ssh.script;

import cn.hutool.core.io.resource.ResourceUtil;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.script.config.ScriptProperties;
import com.nook.framework.ssh.script.internal.ScriptErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 远端脚本执行器
 *
 * @author nook
 */
@Slf4j
@RequiredArgsConstructor
public class RemoteScriptRunner {

    private final ScriptProperties scriptProperties;

    /**
     * 渲染 classpath 模板 + 流式跑, 每行 stdout 回调; 单文件模板专用.
     */
    public void runFromTemplateStreaming(SshSession session,
                                         String classpath,
                                         Map<String, String> vars,
                                         String tmpPrefix,
                                         Duration runTimeout,
                                         Consumer<String> lineConsumer) {
        String script = renderTemplate(classpath, vars);
        runScriptStreaming(session, script, tmpPrefix, runTimeout, lineConsumer);
    }

    /**
     * 流式跑预先拼好的脚本; 模块化 install 用, 调用方负责拼接 + 渲染占位.
     */
    public void runScriptStreaming(SshSession session,
                                   String scriptBody,
                                   String tmpPrefix,
                                   Duration runTimeout,
                                   Consumer<String> lineConsumer) {
        long ts = System.currentTimeMillis();
        String remote = "/tmp/" + tmpPrefix + "-" + ts + ".sh";

        lineConsumer.accept("[nook] 脚本就绪, 大小 " + scriptBody.length() + " bytes");
        lineConsumer.accept("[nook] 上传到远端 " + remote + " ...");
        // try/finally 兜底: upload 写一半失败 / execStream 超时 / bash 在 rm 前被 kill 都让远端 /tmp 残留;
        // finally 静默 rm 防止长期堆积 (主路径里 cmd 自带 rm, 这里是 fallback).
        try {
            session.ssh().uploadString(remote, scriptBody);
            lineConsumer.accept("[nook] 上传完成, 开始执行 (超时 " + runTimeout.toSeconds() + "s)");
            lineConsumer.accept("[nook] ────────────────────────────────────────");

            String cmd = "bash '" + remote + "' 2>&1; rc=$?; rm -f '" + remote + "'; exit $rc";
            session.ssh().execStream(cmd, runTimeout, lineConsumer);

            lineConsumer.accept("[nook] ────────────────────────────────────────");
            lineConsumer.accept("[nook] 远端脚本已结束, 临时文件已清理");
        } finally {
            cleanupQuietly(session, remote, scriptProperties.getCleanupTimeout());
        }
    }

    /**
     * classpath 读模板 + {{KEY}} 占位替换; 模板找不到抛 TEMPLATE_NOT_FOUND.
     */
    public String renderTemplate(String classpath, Map<String, String> vars) {
        String tmpl;
        try {
            tmpl = ResourceUtil.readUtf8Str(classpath);
        } catch (Exception e) {
            log.error("读取脚本模板失败: {}", classpath, e);
            throw new BusinessException(ScriptErrorCode.TEMPLATE_NOT_FOUND, classpath);
        }
        for (Map.Entry<String, String> v : vars.entrySet()) {
            tmpl = tmpl.replace("{{" + v.getKey() + "}}", v.getValue());
        }
        return tmpl;
    }

    /** 远端临时脚本兜底清理; 静默吞错避免覆盖原始失败原因. */
    private static void cleanupQuietly(SshSession session, String remotePath, Duration cleanupTimeout) {
        try {
            String safe = remotePath.replace("'", "'\\''");
            session.ssh().exec("rm -f '" + safe + "'", cleanupTimeout);
        } catch (RuntimeException cleanupErr) {
            log.warn("[script-runner] 清理临时脚本失败 server={} path={}",
                    session.serverId(), remotePath, cleanupErr);
        }
    }
}
