package com.nook.biz.node.framework.xray;

/**
 * Xray 域真常量; 跟具体 server / 部署参数无关的"约定值".
 *
 * @author nook
 */
public final class XrayConstants {

    /** Xray 在远端 systemd 上注册的 unit 名 (50-xray.sh.tmpl 安装时硬编码为此名). */
    public static final String SYSTEMD_UNIT = "xray";

    private XrayConstants() {
    }
}
