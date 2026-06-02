package com.nook.biz.system.api.iptype.dto;

import lombok.Data;

/**
 * 跨模块 - IP 类型 Response DTO
 *
 * @author nook
 */
@Data
public class SystemIpTypeRespDTO {

    private String id;

    /** 类型编码: isp / datacenter / residential */
    private String code;

    private String name;

    private Integer sortOrder;
}
