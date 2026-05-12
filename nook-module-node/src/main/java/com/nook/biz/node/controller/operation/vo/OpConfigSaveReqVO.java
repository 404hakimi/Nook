package com.nook.biz.node.controller.operation.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - Op 配置编辑 Req VO
 *
 * @author nook
 */
@Data
public class OpConfigSaveReqVO {

    @NotNull
    @Min(1)
    @Max(7200)
    private Integer execTimeoutSeconds;

    @NotNull
    @Min(1)
    @Max(7200)
    private Integer waitTimeoutSeconds;

    @NotNull
    @Min(0)
    @Max(10)
    private Integer maxRetry;

    @NotNull
    private Boolean enabled;

    @Size(max = 255)
    private String description;
}
