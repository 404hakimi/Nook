package com.nook.biz.node.framework.server.script;

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
 * Nook 模块脚本注册
 *
 * @author nook
 */
@Slf4j
@Component
public class NookScripts {

    // xray 与 socks5(dante) 装机均已改为"配置 + 通知 agent"(agent 内置 Go 装机), 原 install 脚本退场;
    // swap / bbr 仍由 ServerOsOp 运维调优单独使用, 保留.
    public static final ScriptModule MODULE_SWAP = m("module-swap",
            "scripts/modules/20-swap.sh.tmpl", "swap 文件创建/扩容");
    public static final ScriptModule MODULE_BBR = m("module-bbr",
            "scripts/modules/30-bbr.sh.tmpl", "开启 BBR 拥塞控制");

    /** OS 调优 op 公共 helper, 拼到模块前面. */
    public static final String OPS_HELPERS = "scripts/ops/_helpers.sh";

    /** OS 调优 op 临时脚本前缀; 拼上具体 op 名, 如 "nook-server-ops-swap". */
    public static final String OPS_TMP_PREFIX = "nook-server-ops";

    private static final List<ScriptModule> ALL = List.of(
            MODULE_SWAP, MODULE_BBR);

    @Resource
    private ScriptCatalog scriptCatalog;

    @PostConstruct
    void register() {
        scriptCatalog.registerAll(ALL);
        log.info("[register] 注册 {} 个 ScriptModule 到 catalog", ALL.size());
    }

    private static ScriptModule m(String id, String classpath, String description) {
        return new ScriptModule(id, ScriptCategory.MODULE, classpath, id, description, Set.of());
    }
}
