package com.nook.biz.system.controller.region.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 区域码更正 Request VO
 *
 * <p>oldCode = 原区域码 (主键); 父类 code = 目标新码 + 其余展示字段.
 * 保存时改主键并级联迁移引用该码的机器 / 套餐.
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SystemRegionRecodeReqVO extends SystemRegionSaveReqVO {

    /** 原区域码. */
    @NotBlank(message = "原区域码不能为空")
    private String oldCode;
}
