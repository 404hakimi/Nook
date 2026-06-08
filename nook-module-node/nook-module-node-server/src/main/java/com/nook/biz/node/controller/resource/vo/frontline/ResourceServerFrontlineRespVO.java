package com.nook.biz.node.controller.resource.vo.frontline;

import lombok.Data;

/**
 * 管理后台 - 线路机扩展 Response VO
 *
 * @author nook
 */
@Data
public class ResourceServerFrontlineRespVO {

    /** 服务器编号. */
    private String serverId;
    /** 绑定域名. */
    private String domain;
    /** Cloudflare Zone 编号. */
    private String cfZoneId;
    /** Cloudflare DNS 记录编号. */
    private String cfRecordId;
}
