package com.nook.biz.resource.controller.ip.vo;

import lombok.Data;

/**
 * SOCKS5 连通性测试出参。
 * <p>nook 后端通过该 IP 池条目的 SOCKS5 凭据拨号一个公网 echo-IP 服务, 验证:
 *   1) SOCKS5 服务可达 + 鉴权通过
 *   2) 出网公网 IP 是否符合预期 (用 exitIp 字段比对 ipAddress)
 */
@Data
public class Socks5TestRespVO {

    private boolean success;

    /** 拨号 + 拉到响应总耗时, 毫秒。 */
    private long elapsedMs;

    /** 通过该 SOCKS5 出网时看到的公网 IP; success=true 时有值。 */
    private String exitIp;

    /** success=false 时的失败原因 (鉴权 / 超时 / 拒绝连接 等)。 */
    private String error;
}
