package com.nook.biz.node.framework.server.script;

import cn.hutool.core.io.resource.ResourceUtil;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.framework.ssh.core.SshSession;
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

    /** 兜底清理超时; 远端 rm -f */
    private static final Duration CLEANUP_TIMEOUT = Duration.ofSeconds(20);

    /**
     * 渲染 classpath 模板 + 流式跑, 每行 stdout 回调; 单文件模板专用.
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
        runScriptStreaming(session, script, tmpPrefix, runTimeout, lineConsumer);
    }

    /**
     * 流式跑预先拼好的脚本; 模块化 install 用, 调用方负责拼接 + 渲染占位.
     *
     * @param session      已就绪 SSH 会话
     * @param scriptBody   完整脚本内容
     * @param tmpPrefix    远端 /tmp 文件名前缀
     * @param runTimeout   脚本运行超时
     * @param lineConsumer 每行 stdout 的消费回调
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
            cleanupQuietly(session, remote);
        }
    }

    /**
     * classpath 读模板 + {{KEY}} 占位替换; 模板找不到抛 BACKEND_OPERATION_FAILED.
     *
     * @param classpath classpath 上的模板路径
     * @param vars      占位变量表
     * @return 渲染后的脚本字符串
     */
    public String renderTemplate(String classpath, Map<String, String> vars) {
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

    /**
     * 远端临时脚本兜底清理; 静默吞错避免覆盖原始失败原因.
     *
     * @param session    已就绪 SSH 会话
     * @param remotePath 远端临时脚本绝对路径
     */
    private static void cleanupQuietly(SshSession session, String remotePath) {
        try {
            String safe = remotePath.replace("'", "'\\''");
            session.ssh().exec("rm -f '" + safe + "'", CLEANUP_TIMEOUT);
        } catch (RuntimeException cleanupErr) {
            log.warn("[script-runner] 清理临时脚本失败 server={} path={}",
                    session.serverId(), remotePath, cleanupErr);
        }
    }
}
