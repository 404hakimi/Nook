package com.nook.biz.trade.controller.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 套餐绑定资源 入参.
 *
 * @author nook
 */
@Data
public class BindResourceReqVO {

    @NotBlank(message = "planId 必填")
    private String planId;

    @NotBlank(message = "resourceType 必填")
    private String resourceType;

    @NotBlank(message = "resourceId 必填")
    private String resourceId;
}
