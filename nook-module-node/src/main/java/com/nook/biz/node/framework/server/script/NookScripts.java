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
 * Nook 项目所有远端脚本的中央登记: ScriptModule 常量 + 启动时 register 进 framework 的 ScriptCatalog.
 *
 * <p>调用方拿常量传给 {@code catalog.run(session, NookScripts.NOOK_AGENT_INSTALL, vars, timeout, sink)}.
 *
 * <p>新增脚本: ① 模板放 resources/scripts/{install|uninstall|ops|modules}/ → ② 这里加 public static 常量
 * → ③ 加进 {@link #ALL} 列表自动注册.
 *
 * @author nook
 */
@Slf4j
@Component
public class NookScripts {

    // ============================ INSTALL (一键装机) ============================

    /** Nook agent 装机 (frontline / landing 共用; ROLE placeholder 区分). */
    public static final ScriptModule NOOK_AGENT_INSTALL = new ScriptModule(
            "nook-agent-install",
            ScriptCategory.INSTALL,
            "scripts/install/nook-agent.sh.tmpl",
            "nook-install-agent",
            "Nook agent (frontline / landing) 一键装机, 含 vnstat / journald 限大小 / systemd unit",
            Set.of("SERVER_NAME", "ROLE", "BACKEND_URL", "AGENT_TOKEN", "CONFIG_YAML"));

    /** SOCKS5 落地 (dante-server + PAM). */
    public static final ScriptModule SOCKS5_INSTALL = new ScriptModule(
            "socks5-dante-install",
            ScriptCategory.INSTALL,
            "scripts/install/socks5-dante.sh.tmpl",
            "nook-install-socks5",
            "SOCKS5 落地节点 (dante-server + PAM 用户认证)",
            Set.of());

    // ============================ OPS (运维短操作) ============================

    /** SOCKS5 改密 / 改端口 (不重装包). */
    public static final ScriptModule SOCKS5_UPDATE_CREDS = new ScriptModule(
            "socks5-update-creds",
            ScriptCategory.OPS,
            "scripts/ops/socks5-update-creds.sh.tmpl",
            "nook-update-socks5-creds",
            "SOCKS5 端口 / 用户 / 密码 / 日志 / 防火墙热改",
            Set.of());

    // ============================ MODULE (拼装单元) ============================

    public static final ScriptModule MODULE_PREPARE_ENV = m("module-prepare-env",
            "scripts/modules/00-prepare-env.sh.tmpl", "环境探测 + apt 公共依赖");
    public static final ScriptModule MODULE_TIMEZONE   = m("module-timezone",
            "scripts/modules/10-timezone.sh.tmpl", "强制时区 Asia/Shanghai");
    public static final ScriptModule MODULE_SWAP       = m("module-swap",
            "scripts/modules/20-swap.sh.tmpl", "swap 文件创建/扩容");
    public static final ScriptModule MODULE_BBR        = m("module-bbr",
            "scripts/modules/30-bbr.sh.tmpl", "开启 BBR 拥塞控制");
    public static final ScriptModule MODULE_UFW        = m("module-ufw",
            "scripts/modules/40-ufw.sh.tmpl", "ufw 防火墙基础规则");
    public static final ScriptModule MODULE_ACME_TLS   = m("module-acme-tls",
            "scripts/modules/45-acme-tls.sh.tmpl", "acme.sh + Cloudflare DNS 申请 TLS");
    public static final ScriptModule MODULE_LOGROTATE  = m("module-logrotate",
            "scripts/modules/47-logrotate.sh.tmpl", "logrotate 配置");
    public static final ScriptModule MODULE_XRAY       = m("module-xray",
            "scripts/modules/50-xray.sh.tmpl", "xray 主体安装");
    public static final ScriptModule MODULE_FINALIZE   = m("module-finalize",
            "scripts/modules/99-finalize.sh.tmpl", "systemd 启用 + 收尾自检");

    // ============================ 通用常量 (非 ScriptModule) ============================

    /** OS 调优 op 公共 helper, 拼到模块前面. */
    public static final String OPS_HELPERS = "scripts/ops/_helpers.sh";

    /** OS 调优 op 临时脚本前缀; 拼上具体 op 名, 如 "nook-server-ops-swap". */
    public static final String OPS_TMP_PREFIX = "nook-server-ops";

    /** xray 一键部署临时脚本前缀 (走 assemble 多模块, 不对应单个 ScriptModule). */
    public static final String INSTALL_XRAY_TMP_PREFIX = "nook-install-xray";

    private static final List<ScriptModule> ALL = List.of(
            NOOK_AGENT_INSTALL, SOCKS5_INSTALL, SOCKS5_UPDATE_CREDS,
            MODULE_PREPARE_ENV, MODULE_TIMEZONE, MODULE_SWAP, MODULE_BBR,
            MODULE_UFW, MODULE_ACME_TLS, MODULE_LOGROTATE, MODULE_XRAY, MODULE_FINALIZE);

    @Resource
    private ScriptCatalog scriptCatalog;

    @PostConstruct
    void register() {
        scriptCatalog.registerAll(ALL);
        log.info("[nook-scripts] 注册 {} 个 ScriptModule 到 catalog", ALL.size());
    }

    /** 内部 helper: 给 module 类脚本生成 ScriptModule (tmpPrefix 用 id, 不强校 vars). */
    private static ScriptModule m(String id, String classpath, String description) {
        return new ScriptModule(id, ScriptCategory.MODULE, classpath, id, description, Set.of());
    }
}
