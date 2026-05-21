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
 * <p>xray 三件套 (bin/apiPort/statsInterval) 不在表单里 — frontline role 时 backend 自动从 xray_node
 * 表读已部署 xray 的真实路径 + 端口, 没装 xray 就用代码兜底 (agent 启动自检 bin 不存在不挂 collector).
 * stats_interval 是 agent-side 轮询率, 装机时统一用 300s, 后续 ConfigEditDialog 可改.
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
}
