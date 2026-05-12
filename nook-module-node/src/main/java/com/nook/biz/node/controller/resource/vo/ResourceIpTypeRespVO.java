package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * IP 类型响应; 只读, 由 99_seed.sql 初始化(isp/datacenter/residential 三档)。
 */
@Data
public class ResourceIpTypeRespVO {

    private String id;
    private String code;
    private String name;
    private String description;
    private Integer sortOrder;
    /** IP 退订后冷却分钟数 */
    private Integer coolingMinutes;
}
