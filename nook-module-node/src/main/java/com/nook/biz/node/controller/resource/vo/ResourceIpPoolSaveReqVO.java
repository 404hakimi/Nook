package com.nook.biz.node.controller.resource.vo;

import com.nook.biz.node.enums.ResourceIpPoolLifecycleEnum;
import com.nook.biz.node.enums.ResourceIpPoolProvisionModeEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * IP 池新增 / 编辑 Request VO.
 *
 * @author nook
 */
@Data
public class ResourceIpPoolSaveReqVO {

    @NotBlank(message = "区域不能为空")
    @Size(max = 32, message = "区域码长度不能超过 32")
    @Pattern(regexp = "^[A-Z][A-Z0-9\\-]+$", message = "区域码须大写, e.g., JP-TYO / US-LAX / HK")
    private String region;

    @NotBlank(message = "IP 类型不能为空")
    @Size(max = 36)
    private String ipTypeId;

    /** 装机生命周期; 取值见 {@link ResourceIpPoolLifecycleEnum}. */
    @NotBlank(message = "lifecycle_state 不能为空")
    @Pattern(regexp = "INSTALLING|READY|LIVE|RETIRED", message = "lifecycleState 须为 INSTALLING/READY/LIVE/RETIRED")
    private String lifecycleState;

    /** 部署模式; 取值见 {@link ResourceIpPoolProvisionModeEnum}. */
    @NotNull(message = "部署模式不能为空")
    @Min(value = 1, message = "部署模式值越界")
    @Max(value = 2, message = "部署模式值越界")
    private Integer provisionMode;

    @NotBlank(message = "IP 地址不能为空")
    @Size(max = 45)
    private String ipAddress;

    @NotNull(message = "SOCKS5 端口不能为空")
    @Min(value = 1) @Max(value = 65535)
    private Integer socks5Port;

    @Size(max = 64)
    private String socks5Username;

    /** Update 留空 = 保留原值. */
    @Size(max = 255)
    private String socks5Password;

    @Size(max = 64)
    private String logLevel;

    @NotBlank(message = "logPath 必填")
    @Size(max = 255)
    private String logPath;

    @Min(value = 0) @Max(value = 1)
    private Integer autostartEnabled;

    @Min(value = 0) @Max(value = 1)
    private Integer firewallEnabled;

    @Size(max = 255)
    private String firewallAllowFrom;

    @Size(max = 255)
    private String installDir;

    @Size(max = 128)
    private String sshHost;

    @Min(value = 1) @Max(value = 65535)
    private Integer sshPort;

    @Size(max = 64)
    private String sshUser;

    @Size(max = 255)
    private String sshPassword;

    @Min(value = 1) @Max(value = 1_000_000)
    private Integer bandwidthMbps;

    @Min(value = 1) @Max(value = 10_000_000)
    private Integer trafficQuotaGb;

    /** 月度成本 USD. */
    private BigDecimal costMonthlyUsd;

    @Min(value = 1, message = "账单日 1-28")
    @Max(value = 28, message = "账单日 1-28")
    private Integer billingCycleDay;

    private LocalDate expiresAt;

    @Size(max = 255)
    private String remark;
}
