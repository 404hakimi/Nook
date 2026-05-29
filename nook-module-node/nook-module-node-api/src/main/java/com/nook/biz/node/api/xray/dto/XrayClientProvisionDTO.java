package com.nook.biz.node.api.xray.dto;

import lombok.Data;

/**
 * Xray 客户端开通入参 (跨模块契约; trade 下单时调).
 *
 * @author nook
 */
@Data
public class XrayClientProvisionDTO {

    /** 线路机 server id. */
    private String serverId;

    /** 落地机 server id. */
    private String ipId;

    private String memberUserId;
}
