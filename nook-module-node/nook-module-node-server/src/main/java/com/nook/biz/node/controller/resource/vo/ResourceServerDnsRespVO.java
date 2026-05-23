package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 管理后台 - 服务器 DNS 绑定 Response VO
 *
 * @author nook
 */
@Data
public class ResourceServerDnsRespVO {

    private String serverId;
    private String domain;
    private String cfZoneId;
    private String cfRecordId;
}
