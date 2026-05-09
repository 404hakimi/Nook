package com.nook.biz.node.controller.xray.inbound.vo;

import lombok.Data;

/** 远端 inbound 列表项; 给运营在 IP 关联界面下拉选 inbound 用. */
@Data
public class InboundSnapshotRespVO {

    /** Xray config 里的 tag, 业务侧作为外部 inbound 引用键. */
    private String externalInboundRef;
    private String remark;
    private String protocol;
    private int port;
    private boolean enabled;
    private int clientCount;
}
