package com.nook.biz.node.framework.xray;

/**
 * Xray 跨域约定 tag; install 脚本的 inbound / outbound / routing 都依赖, 改这里需同步改脚本.
 *
 * @author nook
 */
public final class XrayTags {

    private XrayTags() {
    }

    /** API 通道 tag — inbound/outbound/routing 三处共用, 业务流量须避开. */
    public static final String API = "api";
}
