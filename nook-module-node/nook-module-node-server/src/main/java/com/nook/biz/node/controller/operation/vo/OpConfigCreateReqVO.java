package com.nook.biz.node.controller.operation.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - Op 配置创建 Req VO
 *
 * @author nook
 */
@Data
public class OpConfigCreateReqVO {

    @NotBlank
    @Size(max = 64)
    private String opType;

    @NotBlank
    @Size(max = 64)
    private String name;

    @NotNull
    @Min(1)
    @Max(7200)
    private Integer execTimeoutSeconds;

    @NotNull
    @Min(1)
    @Max(7200)
    private Integer waitTimeoutSeconds;

    @Min(0)
    @Max(10)
    private Integer maxRetry;

    private Boolean enabled;

    @Size(max = 255)
    private String description;
}
