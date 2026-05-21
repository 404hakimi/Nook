package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Agent SSH 装机请求; 字段不入库, 仅本次装机用. 前端 dialog 表单 → backend 拼 yaml → SSH 写到远端.
 *
 * <p>每个字段都必填, backend 不持兜底默认; 前端表单负责给合理初值.
 *
 * @author nook
 */
@Data
public class AgentInstallReqVO {

    /** frontline (跑 xray) / landing (跑 socks5). */
    @NotBlank(message = "role 不能为空")
    @Pattern(regexp = "frontline|landing", message = "role 只能是 frontline / landing")
    private String role;

    /** backend HTTP 客户端超时 (秒); 跨境抖动可调大. */
    @NotNull @Min(5) @Max(600)
    private Integer backendTimeoutSeconds;

    /** 心跳间隔 (秒); backend 60s 没收到 → WARN, 300s → OFFLINE. */
    @NotNull @Min(10) @Max(3600)
    private Integer heartbeatIntervalSeconds;

    /** NIC 流量上报间隔 (秒). */
    @NotNull @Min(60) @Max(3600)
    private Integer nicIntervalSeconds;

    /** NIC 网卡名; "auto" 自动用默认路由出口网卡, 多网卡填 eth0/ens5. */
    @NotBlank
    private String nicInterface;

    /** 任务轮询间隔 (秒). */
    @NotNull @Min(5) @Max(600)
    private Integer pollerIntervalSeconds;

    // ============ Frontline 专属 (role=landing 时忽略) ============

    /** xray 二进制路径; agent 启动自检. */
    private String xrayBin;

    /** xray inbound dispatcher API gRPC 端口. */
    @Min(1) @Max(65535)
    private Integer xrayApiPort;

    /** xray 客户流量 stats 上报间隔 (秒). */
    @Min(60) @Max(3600)
    private Integer xrayStatsIntervalSeconds;
}
