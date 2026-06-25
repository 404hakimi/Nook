package com.nook.biz.node.api.xray;

/**
 * Xray 安装基础设施默认值 (单一真理); 前端不再传, 后端装机 + agent config + reconcile 统一引用
 *
 * @author nook
 */
public final class XrayInstallDefaults {

    private XrayInstallDefaults() {
    }

    /** 安装根目录. */
    public static final String INSTALL_DIR = "/home/xray";

    /** xray binary 绝对路径. */
    public static final String XRAY_BINARY_PATH = "/home/xray/bin/xray";

    /** xray config.json 绝对路径. */
    public static final String XRAY_CONFIG_PATH = "/home/xray/config.json";

    /** xray share 目录 (geo*.dat). */
    public static final String XRAY_SHARE_DIR = "/home/xray/bin";

    /** xray 日志目录 (access.log / error.log 父目录). */
    public static final String LOG_DIR = "/home/xray";

    /** systemd unit 文件绝对路径. */
    public static final String SYSTEMD_UNIT_PATH = "/etc/systemd/system/xray.service";

    /** xray 内置 api 端口; 固定 loopback (127.0.0.1) 端口, agent reconcile 据此探测 + 调本地 xray api. */
    public static final int API_PORT = 44944;

    /** TLS 证书路径 (vmess+ws 绑域名时 acme 签发落点); 固定在安装目录 tls 子目录下. */
    public static final String TLS_CERT_PATH = "/home/xray/tls/cert.pem";

    /** TLS 私钥路径. */
    public static final String TLS_KEY_PATH = "/home/xray/tls/key.pem";
}
