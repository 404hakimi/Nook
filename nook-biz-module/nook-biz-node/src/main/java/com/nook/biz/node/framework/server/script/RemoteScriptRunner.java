package com.nook.biz.node.framework.server.script;

import cn.hutool.core.io.resource.ResourceUtil;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 远端脚本执行: classpath 模板渲染 → 上传 /tmp → 执行 → 收集输出.
 *
 * @author nook
 */
@Slf4j
@Component
public class RemoteScriptRunner {

    /** 上传短脚本默认超时. */
    private static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(30);

    /** 兜底清理超时, 短即可 (单条 rm -f). */
    private static final Duration CLEANUP_TIMEOUT = Duration.ofSeconds(5);

    /**
     * 流式跑脚本, 远端 stdout 每来一行回调; nook 自身的进度提示也走 lineConsumer.
     *
     * @param session      已就绪 SSH 会话
     * @param classpath    classpath 上的脚本模板
     * @param vars         模板变量表 ({{KEY}} → value)
     * @param tmpPrefix    远端 /tmp 文件名前缀
     * @param runTimeout   脚本运行超时
     * @param lineConsumer 每行 stdout 的消费回调
     */
    public void runFromTemplateStreaming(SshSession session,
                                         String classpath,
                                         Map<String, String> vars,
                                         String tmpPrefix,
                                         Duration runTimeout,
                                         Consumer<String> lineConsumer) {
        String script = renderTemplate(classpath, vars);
        long ts = System.currentTimeMillis();
        String remote = "/tmp/" + tmpPrefix + "-" + ts + ".sh";

        lineConsumer.accept("[nook] 渲染模板完成, 大小 " + script.length() + " bytes");
        lineConsumer.accept("[nook] 上传到远端 " + remote + " ...");
        // try/finally 兜底: upload 写一半失败 / execStream 超时抛 / bash 在 rm 前被 kill 这些路径都会让远端 /tmp 残留;
        // finally 静默 rm 防止长期堆积 (主路径里 cmd 自带 rm, 这里是 fallback).
        try {
            session.ssh().uploadString(remote, script, UPLOAD_TIMEOUT);
            lineConsumer.accept("[nook] 上传完成, 开始执行(超时 " + runTimeout.toSeconds() + "s)");
            lineConsumer.accept("[nook] ────────────────────────────────────────");

            String cmd = "bash '" + remote + "' 2>&1; rc=$?; rm -f '" + remote + "'; exit $rc";
            session.ssh().execStream(cmd, runTimeout, lineConsumer);

            lineConsumer.accept("[nook] ────────────────────────────────────────");
            lineConsumer.accept("[nook] 远端脚本已结束, 临时文件已清理");
        } finally {
            cleanupQuietly(session, remote);
        }
    }

    /** 远端临时脚本兜底清理; 静默吞错避免覆盖原始失败原因. */
    private static void cleanupQuietly(SshSession session, String remotePath) {
        try {
            String safe = remotePath.replace("'", "'\\''");
            session.ssh().exec("rm -f '" + safe + "'", CLEANUP_TIMEOUT);
        } catch (RuntimeException cleanupErr) {
            log.warn("[script-runner] 清理临时脚本失败 server={} path={}",
                    session.serverId(), remotePath, cleanupErr);
        }
    }

    /** classpath 读模板 + {{KEY}} 占位替换; 模板找不到抛 BACKEND_OPERATION_FAILED. */
    private String renderTemplate(String classpath, Map<String, String> vars) {
        String tmpl;
        try {
            tmpl = ResourceUtil.readUtf8Str(classpath);
        } catch (Exception e) {
            log.error("读取脚本模板失败: {}", classpath, e);
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    "<template>", "无法读取模板 " + classpath);
        }
        for (Map.Entry<String, String> v : vars.entrySet()) {
            tmpl = tmpl.replace("{{" + v.getKey() + "}}", v.getValue());
        }
        return tmpl;
    }
}
