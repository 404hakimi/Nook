package com.nook.biz.node.controller.xray.client.vo;

import lombok.Data;

/**
 * 修改 client 本地元数据; 字段全可空 (传啥改啥); 字段范围由 XrayClientValidator 校验.
 *
 * @author nook
 */
@Data
public class ClientUpdateReqVO {

    private String listenIp;

    private Integer listenPort;

    private String transport;

    /** 1=运行 2=已停 3=待同步 4=远端缺失. */
    private Integer status;
}
