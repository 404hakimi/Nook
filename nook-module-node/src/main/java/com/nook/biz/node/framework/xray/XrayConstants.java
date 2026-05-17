package com.nook.biz.node.framework.xray;

/**
 * Xray 域真常量; 跟具体 server / 部署参数无关的"约定值".
 *
 * @author nook
 */
public final class XrayConstants {

    /** Xray 在远端 systemd 上注册的 unit 名 (50-xray.sh.tmpl 安装时硬编码为此名). */
    public static final String SYSTEMD_UNIT = "xray";

    /** systemd unit 文件绝对路径 (50-xray.sh.tmpl 写到这里). */
    public static final String SYSTEMD_UNIT_PATH = "/etc/systemd/system/xray.service";

    /** 1:N 模型下共享 inbound 的固定 tag, 与 50-xray.sh.tmpl 里 inbound 对齐. */
    public static final String SHARED_INBOUND_TAG = "in_shared";

    /** 业务 outbound tag 前缀; 完整 tag = OUTBOUND_TAG_PREFIX + clientId. */
    public static final String OUTBOUND_TAG_PREFIX = "out_";

    /** 业务 routing rule tag 前缀; 完整 tag = RULE_TAG_PREFIX + clientId. */
    public static final String RULE_TAG_PREFIX = "rule_";

    /** lsrules 输出里 xray 自身的 API 通道 rule tag, 对账时需排除. */
    public static final String BUILTIN_API_RULE_TAG = "api";

    /** 由 clientId 派生 outbound tag. */
    public static String outboundTagOf(String clientId) {
        return OUTBOUND_TAG_PREFIX + clientId;
    }

    /** 由 clientId 派生 routing rule tag. */
    public static String ruleTagOf(String clientId) {
        return RULE_TAG_PREFIX + clientId;
    }

    private XrayConstants() {
    }
}
