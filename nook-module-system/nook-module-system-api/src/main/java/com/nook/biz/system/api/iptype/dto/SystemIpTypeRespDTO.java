package com.nook.biz.system.api.iptype.dto;

import lombok.Data;

/**
 * 跨模块 - IP 类型 Response DTO
 *
 * @author nook
 */
@Data
public class SystemIpTypeRespDTO {

    /** IP 类型ID. */
    private String id;

    /** 类型编码: isp / datacenter / residential. */
    private String code;

    /** 展示名称. */
    private String name;

    /** 排序值, 升序. */
    private Integer sortOrder;
}
