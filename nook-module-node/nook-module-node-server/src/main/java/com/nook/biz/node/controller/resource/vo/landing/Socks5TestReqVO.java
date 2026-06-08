package com.nook.biz.node.controller.resource.vo.landing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 管理后台 - SOCKS5 拨号测试入参
 *
 * @author nook
 */
@Data
public class Socks5TestReqVO {

    /** echo-IP 端点; 必须 http/https. */
    @NotBlank(message = "echoUrl 不能为空")
    @Pattern(regexp = "^https?://.+", message = "echoUrl 必须 http/https")
    private String echoUrl;

    /** TCP 建连超时毫秒; 500-60000. */
    @NotNull(message = "connectTimeoutMs 不能为空")
    @Min(value = 500) @Max(value = 60000)
    private Integer connectTimeoutMs;

    /** HTTP 读响应超时毫秒; 500-60000. */
    @NotNull(message = "readTimeoutMs 不能为空")
    @Min(value = 500) @Max(value = 60000)
    private Integer readTimeoutMs;
}
