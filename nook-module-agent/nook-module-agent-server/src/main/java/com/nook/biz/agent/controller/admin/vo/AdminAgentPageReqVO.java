package com.nook.biz.agent.controller.admin.vo;

import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - Agent 卡片分页 Request VO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminAgentPageReqVO extends PageParam {

    /** 名称模糊匹 (name / domain). */
    private String name;

    /** IP / host 模糊匹. */
    private String host;

    /** 装机生命周期 INSTALLING / READY / LIVE / RETIRED. */
    private String lifecycleState;

    /** 区域码. */
    private String region;
}
