package com.nook.biz.resource.controller.ip.vo;

import com.nook.common.web.request.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** IP 池列表分页查询入参。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceIpPoolPageReqVO extends PageParam {

    /** 关键词，模糊匹配 ip_address */
    private String keyword;

    /** 1=available 2=occupied 3=testing 4=blacklisted 5=cooling 6=degraded */
    private Integer status;

    /** 区域过滤: us-west / jp / hk / sg / ... */
    private String region;

    /** IP 类型过滤: 关联 resource_ip_type.id */
    private String ipTypeId;
}
