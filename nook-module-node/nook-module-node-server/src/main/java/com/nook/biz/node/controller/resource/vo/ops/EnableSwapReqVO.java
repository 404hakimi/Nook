package com.nook.biz.node.controller.resource.vo.ops;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 启用 swap Request VO
 *
 * @author nook
 */
@Data
public class EnableSwapReqVO {

    /** swap 大小 MB. */
    @NotNull(message = "sizeMb 必填")
    @Min(value = 256) @Max(value = 8192)
    private Integer sizeMb;
}
