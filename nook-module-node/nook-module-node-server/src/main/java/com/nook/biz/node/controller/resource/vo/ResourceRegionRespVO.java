package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * 管理后台 - 资源区域 Response VO
 *
 * @author nook
 */
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
