package com.nook.biz.system.controller.region.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理后台 - 区域字典创建 Request VO
 *
 * @author nook
 */
@Data
public class SystemRegionCreateReqVO {

    /** 区域码: JP-TYO / US-LAX / HK 等. */
    @NotBlank(message = "区域码不能为空")
    private String code;

    /** ISO 3166-1 alpha-2 国家码. */
    @NotBlank(message = "国家码不能为空")
    private String countryCode;

    /** 国家名. */
    @NotBlank(message = "国家名不能为空")
    private String countryName;

    /** 城市 (可空). */
    private String city;

    /** 展示名, 如 "日本 · 东京". */
    @NotBlank(message = "展示名不能为空")
    private String displayName;

    /** 国旗 emoji. */
    private String flagEmoji;
}
