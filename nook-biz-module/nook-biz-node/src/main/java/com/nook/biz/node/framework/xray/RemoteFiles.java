package com.nook.biz.node.framework.xray;

/**
 * Xray 远端文件路径 / systemd 单元名 / 项目认可的默认版本, 与 modules/50-xray.sh.tmpl 对齐.
 *
 * @author nook
 */
public final class RemoteFiles {

    private RemoteFiles() {
    }

    /** Xray 配置文件路径; 不同发行版可能不同, 后续走配置化. */
    public static final String CONFIG_PATH = "/usr/local/etc/xray/config.json";

    /** Xray 日志目录默认值. */
    public static final String LOG_DIR = "/var/log/xray";

    /** systemd 单元名. */
    public static final String SYSTEMD_UNIT = "xray";

    /** 远端配置临时上传目录前缀; config-sync 写入新配置时用. */
    public static final String TMP_UPLOAD_PREFIX = "/tmp/nook-xray-config-";

    /** 项目认可的 Xray 稳定版本; 前端 install 表单的默认值, 升级时改这一行. */
    public static final String XRAY_DEFAULT_VERSION = "v1.8.23";
}
