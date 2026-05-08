package com.nook.biz.xray.constant;

/** Xray 对接相关的全局常量；远端路径 / 标签 / stats key 等硬编码统一收口。 */
public final class XrayConstants {

    private XrayConstants() {
    }

    // ===== 远端路径 =====

    /** Xray 配置文件路径 (与 install-line-server.sh.tmpl 对齐, 不同发行版可能不同, 后续走配置化)。 */
    public static final String REMOTE_CONFIG_PATH = "/usr/local/etc/xray/config.json";

    /** Xray 日志目录默认值 (install 脚本 LOG_DIR 默认值)。 */
    public static final String REMOTE_LOG_DIR = "/var/log/xray";

    /** systemd 单元名。 */
    public static final String SYSTEMD_UNIT = "xray";

    // ===== Inbound / Outbound tag 命名规约 =====

    /** API 通道 inbound + outbound 共用 tag, 与 install 脚本对齐。 */
    public static final String API_TAG = "api";

    /** 默认直连出站 tag。 */
    public static final String DIRECT_OUTBOUND_TAG = "direct";

    /** 用户专属 socks5 出站 tag 前缀; 拼接 client email。 */
    public static final String USER_OUTBOUND_TAG_PREFIX = "out_";

    // ===== Stats key 模板 (Xray 命名规约) =====
    // 参考: https://xtls.github.io/document/level-1/api.html  stats 命名形式 user>>>{email}>>>traffic>>>{up|down}link

    /** 用户上行流量字节数 stat 名 (参数: email)。 */
    public static final String STAT_USER_UPLINK_FORMAT = "user>>>%s>>>traffic>>>uplink";
    public static final String STAT_USER_DOWNLINK_FORMAT = "user>>>%s>>>traffic>>>downlink";

    /** API inbound 上行流量, 用于 verifyConnectivity 探活 (always-exist 一旦 statsInboundUplink=true 启用)。 */
    public static final String STAT_API_INBOUND_UPLINK = "inbound>>>api>>>traffic>>>uplink";

    // ===== gRPC 错误识别 =====
    // Xray 上游 HandlerService 用 errors.New(...) 抛错 → gRPC code=UNKNOWN, 仅靠 description 字符串区分;
    // 这是 Xray 设计限制, 我们把字符串关键词集中在这里, 上游 message 改了只需在此处更新。

    /** AlterInbound AddUser 时, email 已存在的 description 关键词。 */
    public static final String GRPC_DESC_USER_DUPLICATE = "already exists";

    /** AlterInbound RemoveUser 时, email 不存在的 description 关键词。 */
    public static final String GRPC_DESC_USER_NOT_FOUND = "not found";

    // ===== Reconcile 命名 =====

    /** 远端配置临时上传目录 + 前缀。 */
    public static final String REMOTE_TMP_PREFIX = "/tmp/nook-xray-config-";
}
