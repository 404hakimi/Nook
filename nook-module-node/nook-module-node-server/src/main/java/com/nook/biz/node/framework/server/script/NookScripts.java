package com.nook.biz.node.framework.server.script;

import com.nook.framework.ssh.script.ScriptCatalog;
import com.nook.framework.ssh.script.ScriptCategory;
import com.nook.framework.ssh.script.ScriptModule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class NookScripts {

    public static final ScriptModule SOCKS5_INSTALL = new ScriptModule(
            "socks5-dante-install",
            ScriptCategory.INSTALL,
            "scripts/install/socks5-dante.sh.tmpl",
            "nook-install-socks5",
            "SOCKS5 落地节点 (dante-server + PAM)",
            Set.of());

    public static final ScriptModule MODULE_PREPARE_ENV = m("module-prepare-env",
            "scripts/modules/00-prepare-env.sh.tmpl", "环境探测 + apt 公共依赖");
    public static final ScriptModule MODULE_TIMEZONE = m("module-timezone",
            "scripts/modules/10-timezone.sh.tmpl", "强制时区 Asia/Shanghai");
    public static final ScriptModule MODULE_SWAP = m("module-swap",
            "scripts/modules/20-swap.sh.tmpl", "swap 文件创建/扩容");
    public static final ScriptModule MODULE_BBR = m("module-bbr",
            "scripts/modules/30-bbr.sh.tmpl", "开启 BBR 拥塞控制");
    public static final ScriptModule MODULE_UFW = m("module-ufw",
            "scripts/modules/40-ufw.sh.tmpl", "ufw 防火墙基础规则");
    public static final ScriptModule MODULE_ACME_TLS = m("module-acme-tls",
            "scripts/modules/45-acme-tls.sh.tmpl", "acme.sh + Cloudflare DNS 申请 TLS");
    public static final ScriptModule MODULE_LOGROTATE = m("module-logrotate",
            "scripts/modules/47-logrotate.sh.tmpl", "logrotate 配置");
    public static final ScriptModule MODULE_XRAY = m("module-xray",
            "scripts/modules/50-xray.sh.tmpl", "xray 主体安装");
    public static final ScriptModule MODULE_FINALIZE = m("module-finalize",
            "scripts/modules/99-finalize.sh.tmpl", "systemd 启用 + 收尾自检");

    /** OS 调优 op 公共 helper, 拼到模块前面. */
    public static final String OPS_HELPERS = "scripts/ops/_helpers.sh";

    /** OS 调优 op 临时脚本前缀; 拼上具体 op 名, 如 "nook-server-ops-swap". */
    public static final String OPS_TMP_PREFIX = "nook-server-ops";

    /** xray 一键部署临时脚本前缀 (走 assemble 多模块, 不对应单个 ScriptModule). */
    public static final String INSTALL_XRAY_TMP_PREFIX = "nook-install-xray";

    private static final List<ScriptModule> ALL = List.of(
            SOCKS5_INSTALL,
            MODULE_PREPARE_ENV, MODULE_TIMEZONE, MODULE_SWAP, MODULE_BBR,
            MODULE_UFW, MODULE_ACME_TLS, MODULE_LOGROTATE, MODULE_XRAY, MODULE_FINALIZE);

    private final ScriptCatalog scriptCatalog;

    @PostConstruct
    void register() {
        scriptCatalog.registerAll(ALL);
        log.info("[register] 注册 {} 个 ScriptModule 到 catalog", ALL.size());
    }

    private static ScriptModule m(String id, String classpath, String description) {
        return new ScriptModule(id, ScriptCategory.MODULE, classpath, id, description, Set.of());
    }
}
