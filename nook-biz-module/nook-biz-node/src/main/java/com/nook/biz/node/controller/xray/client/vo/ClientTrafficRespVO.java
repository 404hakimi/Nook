package com.nook.biz.node.controller.xray.client.vo;

import lombok.Data;

@Data
public class ClientTrafficRespVO {

    private String inboundEntityId;
    private String clientEmail;
    private long upBytes;
    private long downBytes;
    /** 流量上限(字节)；0=不限 */
    private long totalBytes;
    /** 到期时间(毫秒)；0=永久 */
    private long expiryEpochMillis;
    private boolean enabled;
}
