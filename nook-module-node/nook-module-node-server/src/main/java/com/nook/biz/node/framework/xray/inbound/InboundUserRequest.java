package com.nook.biz.node.framework.xray.inbound;

import lombok.Builder;
import lombok.Data;

/**
 * Xray inbound 加 user (adu) 请求入参; 渲染成 xray clients[]
 *
 * @author nook
 */
@Data
@Builder
public class InboundUserRequest {

    /** client email; 同 server 全局唯一. */
    private String email;

    /** 协议级凭据 (vmess/vless 走 UUID, trojan 走密码). */
    private String uuid;
}
