package com.nook.biz.node.controller.socks5.vo;

import lombok.Data;

/**
 * SOCKS5 连通性测试出参; 后端拨号 echo-IP 服务验证:
 *   1) SOCKS5 服务可达 + 鉴权通过
 *   2) 出网公网 IP 与登记 ipAddress 是否匹配 (用 exitIp 字段比对)
 */
@Data
public class Socks5TestRespVO {

    private boolean success;

    /** 拨号 + 拉到响应总耗时, 毫秒. */
    private long elapsedMs;

    /** 通过该 SOCKS5 出网时看到的公网 IP; success=true 时有值. */
    private String exitIp;

    /** success=false 时的失败原因 (鉴权 / 超时 / 拒绝连接 等). */
    private String error;
}
