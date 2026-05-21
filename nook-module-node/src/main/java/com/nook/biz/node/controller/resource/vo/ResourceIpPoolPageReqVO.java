package com.nook.biz.node.controller.resource.vo;

import com.nook.biz.node.enums.ResourceIpPoolLifecycleEnum;
import com.nook.biz.node.enums.ResourceIpPoolStatusEnum;
import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * IP 池分页查询 Request VO.
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceIpPoolPageReqVO extends PageParam {

    /** 关键词; 模糊匹配 ip_address. */
    private String keyword;

    /** 装机生命周期过滤; 取值见 {@link ResourceIpPoolLifecycleEnum}. */
    private String lifecycleState;

    /** 占用状态过滤; 取值见 {@link ResourceIpPoolStatusEnum}. */
    private String status;

    /** 区域过滤 (区域码: JP-TYO / US-LAX 等). */
    private String region;

    /** IP 类型过滤; 关联 resource_ip_type.id. */
    private String ipTypeId;
}
