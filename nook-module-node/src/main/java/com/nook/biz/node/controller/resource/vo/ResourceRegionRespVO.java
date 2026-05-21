package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/** 区域字典 RespVO. */
@Data
public class ResourceRegionRespVO {

    private String code;
    private String countryCode;
    private String countryName;
    private String city;
    private String displayName;
    private String flagEmoji;
    private Integer enabled;
}
