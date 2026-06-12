package com.nook.biz.system.controller.iptype.vo;

import lombok.Data;

/**
 * 管理后台 - IP 类型 Response VO
 *
 * @author nook
 */
@Data
public class SystemIpTypeRespVO {

    /** IP 类型ID. */
    private String id;

    /** 类型编码: isp / datacenter / residential. */
    private String code;

    /** 展示名称. */
    private String name;

    /** 描述. */
    private String description;

    /** 排序值, 升序. */
    private Integer sortOrder;
}
