package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 管理后台 - 线路机扩展 Response VO
 *
 * @author nook
 */
@Data
public class ResourceServerFrontlineRespVO {

    private String serverId;
    private String domain;
    private String cfZoneId;
    private String cfRecordId;
}
