package com.nook.biz.system.controller.region.vo;

import lombok.Data;

/**
 * 管理后台 - 区域字典 Response VO
 *
 * @author nook
 */
@Data
public class SystemRegionRespVO {

    private String code;
    private String countryCode;
    private String countryName;
    private String city;
    private String displayName;
    private String flagEmoji;
    private Integer enabled;
}
