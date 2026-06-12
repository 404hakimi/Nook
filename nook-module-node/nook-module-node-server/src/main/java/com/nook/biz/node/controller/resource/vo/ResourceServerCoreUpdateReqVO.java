package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 服务器核心字段更新 Request VO (lifecycle 走专门 transition 接口)
 *
 * @author nook
 */
@Data
public class ResourceServerCoreUpdateReqVO {

    /** 服务器别名; 全局唯一. */
    @NotBlank(message = "服务器别名不能为空")
    @Size(max = 64, message = "name 长度不能超过 64")
    private String name;

    /** 区域码. */
    @NotBlank(message = "区域不能为空")
    @Size(max = 32, message = "区域码长度不能超过 32")
    @Pattern(regexp = "^[A-Z][A-Z0-9\\-]+$", message = "区域码须大写, e.g., JP-TYO / US-LAX / HK")
    private String region;

    /** 备注. */
    @Size(max = 512)
    private String remark;
}
