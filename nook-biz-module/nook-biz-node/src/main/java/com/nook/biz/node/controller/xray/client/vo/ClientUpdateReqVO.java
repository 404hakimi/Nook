package com.nook.biz.node.controller.xray.client.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 修改 client 本地元数据 (listenIp/listenPort/transport/status); 不触达远端, 协议契约字段不开放修改. */
@Data
public class ClientUpdateReqVO {

    @Size(max = 45, message = "listenIp 长度不能超过 45")
    private String listenIp;

    @Min(value = 1, message = "listenPort 最小 1")
    @Max(value = 65535, message = "listenPort 最大 65535")
    private Integer listenPort;

    @Size(max = 32, message = "transport 长度不能超过 32")
    private String transport;

    /** 1=运行 2=已停 3=待同步 4=远端缺失 */
    @Min(value = 1, message = "status 取值 1-4")
    @Max(value = 4, message = "status 取值 1-4")
    private Integer status;
}
