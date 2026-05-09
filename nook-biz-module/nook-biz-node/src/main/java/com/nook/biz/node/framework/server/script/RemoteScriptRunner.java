package com.nook.biz.node.framework.server.script;

import cn.hutool.core.io.resource.ResourceUtil;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.server.session.ServerSession;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/** 远端脚本执行: classpath 模板渲染 → 上传 /tmp → 执行 → 收集输出. */
@Slf4j
@Component
public class RemoteScriptRunner {

    /** 上传短脚本默认超时. */
    private static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(30);

    /** 渲染模板上传执行; 整段 stdout 一次性返回. */
    public String runFromTemplate(ServerSession session,
                                  String classpath,
                                  Map<String, String> vars,
                                  String tmpPrefix,
                                  Duration runTimeout) {
        String script = renderTemplate(classpath, vars);
        long ts = System.currentTimeMillis();
        String remote = "/tmp/" + tmpPrefix + "-" + ts + ".sh";
        log.info("[script-runner] upload {} bytes -> {} server={}",
                script.length(), remote, session.serverId());
        session.ssh().uploadString(remote, script, UPLOAD_TIMEOUT);
        log.info("[script-runner] running {} server={}", remote, session.serverId());
        try {
            String out = session.ssh().exec(
                    "bash '" + remote + "' 2>&1; rc=$?; rm -f '" + remote + "'; exit $rc",
                    runTimeout
            ).stdout();
            log.info("[script-runner] OK server={} outputBytes={}", session.serverId(), out.length());
            return out;
        } catch (BusinessException be) {
            log.warn("[script-runner] FAIL server={} code={} msg={}",
                    session.serverId(), be.getCode(), be.getMessage());
            throw be;
        }
    }

    /** 流式版本: 远端 stdout 每来一行回调; nook 自身的进度提示也走 lineConsumer. */
    public void runFromTemplateStreaming(ServerSession session,
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
        session.ssh().uploadString(remote, script, UPLOAD_TIMEOUT);
        lineConsumer.accept("[nook] 上传完成, 开始执行(超时 " + runTimeout.toSeconds() + "s)");
        lineConsumer.accept("[nook] ────────────────────────────────────────");

        String cmd = "bash '" + remote + "' 2>&1; rc=$?; rm -f '" + remote + "'; exit $rc";
        session.ssh().execStream(cmd, runTimeout, lineConsumer);

        lineConsumer.accept("[nook] ────────────────────────────────────────");
        lineConsumer.accept("[nook] 远端脚本已结束, 临时文件已清理");
    }

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
