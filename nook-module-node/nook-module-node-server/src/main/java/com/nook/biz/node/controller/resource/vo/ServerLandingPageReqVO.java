package com.nook.biz.node.controller.resource.vo;

import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - SOCKS5 落地节点分页查询 Request VO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ServerLandingPageReqVO extends PageParam {

    /** 关键词 (name / ipAddress 模糊). */
    private String keyword;

    /** 区域过滤. */
    private String region;

    /** IP 类型过滤. */
    private String ipTypeId;

    /** 装机生命周期过滤 (server.lifecycle_state). */
    private String lifecycleState;

    /** 占用状态过滤 (landing.status). */
    private String status;
}
