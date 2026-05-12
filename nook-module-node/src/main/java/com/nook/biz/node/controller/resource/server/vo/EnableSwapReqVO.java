package com.nook.biz.node.controller.resource.server.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启用 swap 入参.
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
