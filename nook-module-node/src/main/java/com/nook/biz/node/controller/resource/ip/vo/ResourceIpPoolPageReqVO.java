package com.nook.biz.node.controller.resource.ip.vo;

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

    /** 关键词; 模糊匹配 ip_address */
    private String keyword;

    /** 状态; 取值见 {@link ResourceIpPoolStatusEnum} */
    private Integer status;

    /** 区域过滤 (us-west / jp / hk / sg 等) */
    private String region;

    /** IP 类型过滤; 关联 resource_ip_type.id */
    private String ipTypeId;
}
