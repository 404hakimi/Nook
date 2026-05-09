package com.nook.biz.node.framework.xray;

/** Xray 远端文件路径与 systemd 单元名 (与 install-line-server.sh.tmpl 对齐). */
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
}
