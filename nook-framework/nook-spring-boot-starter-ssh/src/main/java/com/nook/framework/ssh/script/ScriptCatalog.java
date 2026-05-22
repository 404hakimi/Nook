package com.nook.framework.ssh.script;

import com.nook.common.web.exception.BusinessException;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.script.internal.ScriptErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** 脚本注册 + 执行 facade; 框架不持模块常量, 业务侧 @PostConstruct 调 register/registerAll 注入. */
@Slf4j
@RequiredArgsConstructor
public class ScriptCatalog {

    private final RemoteScriptRunner scriptRunner;

    private final Map<String, ScriptModule> registry = new LinkedHashMap<>();

    /**
     * 注册模块; 重复 id 后注册的会覆盖前面的并打 warn (装机 misconfig 时容易看到).
     */
    public void register(ScriptModule module) {
        ScriptModule prev = registry.put(module.id(), module);
        if (prev != null) {
            log.warn("[script-catalog] 模块 id 重复: {} (原 classpath={}, 新 classpath={})",
                    module.id(), prev.classpath(), module.classpath());
        } else {
            log.debug("[script-catalog] 注册: {} → {}", module.id(), module.classpath());
        }
    }

    /** 批量注册. */
    public void registerAll(Collection<ScriptModule> modules) {
        modules.forEach(this::register);
    }

    // ============================ 查询 ============================

    public List<ScriptModule> list() {
        return registry.values().stream()
                .sorted(Comparator.comparing(ScriptModule::category).thenComparing(ScriptModule::id))
                .toList();
    }

    public List<ScriptModule> listByCategory(ScriptCategory category) {
        return registry.values().stream().filter(m -> m.category() == category).toList();
    }

    public Optional<ScriptModule> byId(String id) {
        return Optional.ofNullable(registry.get(id));
    }

    // ============================ 执行 ============================

    /**
     * 单模板远端跑: render + 校验 vars + 上传 + 流式执行.
     */
    public void run(SshSession session, ScriptModule module, Map<String, String> vars,
                    Duration runTimeout, Consumer<String> lineConsumer) {
        validateVars(module, vars);
        lineConsumer.accept("[nook] 脚本: " + module.id() + " (" + module.description() + ")");
        scriptRunner.runFromTemplateStreaming(session, module.classpath(), vars,
                module.tmpPrefix(), runTimeout, lineConsumer);
    }

    /**
     * 拼装多模块到一份完整 bash: 加 shebang + set -euo pipefail, 各模块 render 后顺序拼接.
     */
    public String assemble(List<ScriptModule> modules, Map<String, String> vars) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("#!/usr/bin/env bash\n");
        sb.append("set -euo pipefail\n\n");
        for (ScriptModule m : modules) {
            validateVars(m, vars);
            sb.append("# ===== ").append(m.id()).append(" — ").append(m.description()).append(" =====\n");
            sb.append(scriptRunner.renderTemplate(m.classpath(), vars)).append("\n");
        }
        return sb.toString();
    }

    /** 仅渲染, 不跑; 调试 / 拼装 OS op 用. */
    public String render(ScriptModule module, Map<String, String> vars) {
        validateVars(module, vars);
        return scriptRunner.renderTemplate(module.classpath(), vars);
    }

    // ============================ 内部 ============================

    private void validateVars(ScriptModule m, Map<String, String> vars) {
        if (m.requiredVars() == null || m.requiredVars().isEmpty()) return;
        List<String> missing = new ArrayList<>();
        for (String key : m.requiredVars()) {
            if (!vars.containsKey(key) || vars.get(key) == null || vars.get(key).isBlank()) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ScriptErrorCode.TEMPLATE_VAR_MISSING,
                    m.id(), String.join(",", missing));
        }
    }

}
