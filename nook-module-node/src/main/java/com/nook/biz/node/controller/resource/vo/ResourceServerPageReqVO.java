package com.nook.biz.node.controller.resource.vo;

import com.nook.biz.node.enums.ResourceServerLifecycleEnum;
import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 服务器分页查询 Request VO.
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceServerPageReqVO extends PageParam {

    /** 关键词; 模糊匹配 name / host / domain. */
    private String keyword;

    /** 装机生命周期过滤; 取值见 {@link ResourceServerLifecycleEnum}. */
    private String lifecycleState;

    /** 区域过滤 (区域码: JP-TYO / US-LAX 等). */
    private String region;
}
