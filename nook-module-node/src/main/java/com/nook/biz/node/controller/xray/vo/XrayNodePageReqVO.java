package com.nook.biz.node.controller.xray.vo;

import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - Xray 节点分页 Req VO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class XrayNodePageReqVO extends PageParam {

    /** 服务器编号; 精确匹配, 用于查单台 server 的节点 */
    private String serverId;

    /** Xray 版本前缀模糊匹配 (如 1.8 / v1.8.23) */
    private String xrayVersion;
}
