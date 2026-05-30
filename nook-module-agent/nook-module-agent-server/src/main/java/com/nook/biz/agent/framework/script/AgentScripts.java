package com.nook.biz.agent.framework.script;

import com.nook.framework.ssh.script.ScriptCatalog;
import com.nook.framework.ssh.script.ScriptCategory;
import com.nook.framework.ssh.script.ScriptModule;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Agent 模块脚本注册
 *
 * @author nook
 */
@Slf4j
@Component
public class AgentScripts {

    /** Agent 装机脚本; 跟 agent-server resources/scripts/install/nook-agent.sh.tmpl 对齐. */
    public static final ScriptModule NOOK_AGENT_INSTALL = new ScriptModule(
            "nook-agent-install",
            ScriptCategory.INSTALL,
            "scripts/install/nook-agent.sh.tmpl",
            "nook-install-agent",
            "Nook agent (frontline / landing) 一键装机",
            Set.of("SERVER_NAME", "ROLE", "BACKEND_URL", "AGENT_TOKEN", "CONFIG_YAML",
                    "NOOK_HOME", "BIN_PATH", "CONFIG_PATH", "SYSTEMD_UNIT_PATH"));

    @Resource
    private ScriptCatalog scriptCatalog;

    @PostConstruct
    void register() {
        scriptCatalog.registerAll(List.of(NOOK_AGENT_INSTALL));
        log.info("[register] agent-server 注册 1 个 ScriptModule (NOOK_AGENT_INSTALL)");
    }
}
