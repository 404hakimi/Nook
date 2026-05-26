package com.nook.biz.system.controller.iptype.vo;

import lombok.Data;

/**
 * 管理后台 - IP 类型 Response VO
 *
 * @author nook
 */
@Data
public class SystemIpTypeRespVO {

    private String id;
    private String code;
    private String name;
    private String description;
    private Integer sortOrder;
    /** IP 退订后冷却分钟数 */
    private Integer coolingMinutes;
}
