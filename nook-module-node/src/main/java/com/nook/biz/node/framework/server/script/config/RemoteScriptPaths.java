package com.nook.biz.node.framework.server.script.config;

/**
 * 远端脚本资源路径 + tmp 前缀的集中配置点; install / OS 调优 / SOCKS5 部署等多处复用.
 *
 * @author nook
 */
public final class RemoteScriptPaths {

    private RemoteScriptPaths() {
    }

    /** install 链路模块脚本目录前缀; 拼具体模块名形如 "scripts/modules/50-xray.sh.tmpl". */
    public static final String INSTALL_MODULES_DIR = "scripts/modules/";

    /** OS 调优 op 公共 helper (log/warn + root 校验), runOsOp 时拼到模块前面. */
    public static final String OPS_HELPERS = "scripts/ops/_helpers.sh";

    /** SOCKS5 落地节点单文件部署模板 (基于 dante-server + PAM 用户认证). */
    public static final String SOCKS5_INSTALL_TMPL = "scripts/install-dante-landing.sh.tmpl";

    /** SOCKS5 凭据 / 配置热更新模板 (端口/用户/密码/日志/防火墙改动, 不重装包). */
    public static final String SOCKS5_UPDATE_CREDS_TMPL = "scripts/update-dante-creds.sh.tmpl";

    /** Xray 一键部署/重装临时脚本前缀. */
    public static final String INSTALL_XRAY_TMP = "nook-install-xray";

    /** SOCKS5 落地部署临时脚本前缀. */
    public static final String INSTALL_SOCKS5_TMP = "nook-install-socks5";

    /** SOCKS5 凭据热更新临时脚本前缀. */
    public static final String UPDATE_SOCKS5_CREDS_TMP = "nook-update-socks5-creds";

    /** OS 调优 op 临时脚本前缀; 后面会拼上具体 op 名 (e.g. "nook-server-ops-swap"). */
    public static final String OPS_TMP = "nook-server-ops";
}
