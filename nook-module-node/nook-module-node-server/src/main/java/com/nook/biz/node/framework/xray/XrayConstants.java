package com.nook.biz.node.framework.xray;

/**
 * Xray 域真常量; 跟具体 server / 部署参数无关的"约定值".
 *
 * @author nook
 */
public final class XrayConstants {

    /**
     * Xray 在远端 systemd 上注册的 unit 名 (与 agent xraydeploy 写的 systemd unit 名 "xray" 对齐).
     * <p>注: unit 文件 *路径* 不再走常量, 而是装机时由后台固定默认落库 (XrayInstallDO.xraySystemdUnitPath);
     * 这里只保留 *服务名* 供后续运维操作 (XrayDaemonControl 的 systemctl restart / is-active 等) 使用.
     */
    public static final String SYSTEMD_UNIT = "xray";

    /** 1:N 模型下共享 inbound 的固定 tag, 与 agent 写入 config.json 的共享 inbound tag 对齐. */
    public static final String SHARED_INBOUND_TAG = "in_shared";

    /** 业务 outbound tag 前缀; 完整 tag = OUTBOUND_TAG_PREFIX + clientId + "_" + ipId. */
    public static final String OUTBOUND_TAG_PREFIX = "out_";

    /** 业务 routing rule tag 前缀; 完整 tag = RULE_TAG_PREFIX + clientId + "_" + ipId. */
    public static final String RULE_TAG_PREFIX = "rule_";

    /** 由 clientId + 落地机 ipId 派生 outbound tag; 编入 ipId 使换落地机即 tag 变, agent 靠旧删新建收敛. */
    public static String outboundTagOf(String clientId, String ipId) {
        return OUTBOUND_TAG_PREFIX + clientId + "_" + ipId;
    }

    /** 由 clientId + 落地机 ipId 派生 routing rule tag; 与 outbound tag 同步编入 ipId(rule 引用 outbound). */
    public static String ruleTagOf(String clientId, String ipId) {
        return RULE_TAG_PREFIX + clientId + "_" + ipId;
    }

    private XrayConstants() {
    }
}
