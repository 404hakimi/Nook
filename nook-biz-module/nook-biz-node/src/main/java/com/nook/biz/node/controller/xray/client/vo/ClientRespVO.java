package com.nook.biz.node.controller.xray.client.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ClientRespVO {

    private String id;
    private String serverId;
    private String ipId;
    private String memberUserId;
    private String externalInboundRef;
    private String protocol;
    private String transport;
    private String listenIp;
    private Integer listenPort;
    /**
     * 协议密钥；属敏感字段(规范 §11)。
     * list 页走 mask(前 8 + *** + 后 8)避免在表格里大面积曝光；detail 页应直接读 entity 拿明文回填编辑表单。
     * 当前 controller 的 list / detail 都用同一份 RespVO，统一 mask；明文取值通过专门的 secret-reveal 接口暴露(后续做)。
     */
    private String clientUuid;
    private String clientEmail;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSyncedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
