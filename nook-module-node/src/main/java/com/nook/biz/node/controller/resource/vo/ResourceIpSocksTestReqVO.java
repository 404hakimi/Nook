package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SOCKS5 连通性测试入参; 全部字段必填, 后端不再兜底, 默认值由前端弹框预填后用户可改.
 *
 * @author nook
 */
@Data
public class ResourceIpSocksTestReqVO {

    /** echo-IP 端点; http/https URL */
    @NotBlank(message = "echoUrl 不能为空")
    @Pattern(regexp = "^https?://.+", message = "echoUrl 必须是 http/https URL")
    @Size(max = 256, message = "echoUrl 最长 256 字符")
    private String echoUrl;

    /** TCP 建连超时, 毫秒; 上限 60s 防被慢链路拖死 */
    @NotNull(message = "connectTimeoutMs 不能为空")
    @Min(value = 500, message = "connectTimeoutMs 不能小于 500ms")
    @Max(value = 60_000, message = "connectTimeoutMs 不能大于 60000ms")
    private Integer connectTimeoutMs;

    /** HTTP 读响应超时, 毫秒; 上限 60s */
    @NotNull(message = "readTimeoutMs 不能为空")
    @Min(value = 500, message = "readTimeoutMs 不能小于 500ms")
    @Max(value = 60_000, message = "readTimeoutMs 不能大于 60000ms")
    private Integer readTimeoutMs;
}
