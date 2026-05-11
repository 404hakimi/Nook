package com.nook.biz.node.controller.xray.client.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改 client 本地元数据; 字段全可空 (传啥改啥, null 保留原值).
 *
 * @author nook
 */
@Data
public class ClientUpdateReqVO {

    @Size(max = 45, message = "listenIp 长度需 ≤ 45")
    private String listenIp;

    @Min(value = 1) @Max(value = 65535)
    private Integer listenPort;

    @Size(max = 32, message = "transport 长度需 ≤ 32")
    private String transport;

    /** 1=运行 2=已停 3=待同步 4=远端缺失. */
    @Min(value = 1) @Max(value = 4)
    private Integer status;
}
