package com.nook.biz.system.controller.region.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 区域码更正 Request VO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SystemRegionRecodeReqVO extends SystemRegionCreateReqVO {

    /** 原区域码; 父类 code 为目标新码. */
    @NotBlank(message = "原区域码不能为空")
    private String oldCode;
}
