package com.nook.biz.agent.controller.vo;

import com.nook.biz.agent.api.enums.AgentRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 管理后台 - Agent 装机 Request VO
 *
 * @author nook
 */
@Data
public class AgentInstallReqVO {

    /** agent 角色; frontline (跑 xray) / landing (跑 socks5). */
    @NotBlank(message = "role 不能为空")
    @Pattern(regexp = AgentRole.Codes.PATTERN, message = "role 只能是 frontline / landing")
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

    // ==================== 路径 + URL (前端默认 + 可改; backend 不兜底) ====================

    /** 装机根目录 (e.g., /home/nook-agent). */
    @NotBlank
    private String nookHome;

    /** Agent binary 绝对路径 (e.g., /home/nook-agent/nook-agent). */
    @NotBlank
    private String binPath;

    /** Agent config.yml 绝对路径 (e.g., /home/nook-agent/config.yml). */
    @NotBlank
    private String configPath;

    /** systemd unit 路径 (e.g., /etc/systemd/system/nook-agent.service). */
    @NotBlank
    private String systemdUnitPath;

    /** Backend 公网 URL (agent yaml + 装机脚本 curl binary 都用); 前端可从 meta 拿默认后改. */
    @NotBlank
    private String backendUrl;

    /** Frontline 必填: xray binary 绝对路径 (前端从 install-meta 取). landing 忽略. */
    private String xrayBin;

    /** Frontline 必填: xray api server 端口 (1-65535). landing 忽略. */
    @Min(1) @Max(65535)
    private Integer xrayApiPort;

    // ==================== SSH 参数 (per-install override; 不回写 resource_server 表) ====================

    /** SSH 握手超时 (秒). */
    @NotNull @Min(5) @Max(600)
    private Integer sshTimeoutSeconds;

    /** SSH 单条命令最大耗时 (秒). */
    @NotNull @Min(5) @Max(300)
    private Integer sshOpTimeoutSeconds;

    /** SCP 上传单文件超时 (秒). */
    @NotNull @Min(5) @Max(600)
    private Integer sshUploadTimeoutSeconds;

    /** 安装脚本整体超时 (秒); 慢链路 binary 下载 + xray 启动慢时调大. */
    @NotNull @Min(60) @Max(3600)
    private Integer installTimeoutSeconds;
}
