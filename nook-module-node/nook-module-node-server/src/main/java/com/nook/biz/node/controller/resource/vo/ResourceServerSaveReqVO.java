package com.nook.biz.node.controller.resource.vo;

import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
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
 * 服务器新增 / 编辑 Request VO.
 *
 * @author nook
 */
@Data
public class ResourceServerSaveReqVO {

    @NotBlank(message = "服务器别名不能为空")
    @Size(max = 64, message = "name 长度不能超过 64")
    private String name;

    @NotBlank(message = "管理 IP / 域名不能为空")
    @Size(max = 128, message = "host 长度不能超过 128")
    private String host;

    @NotNull(message = "SSH 端口不能为空")
    @Min(value = 1, message = "SSH 端口范围 1-65535")
    @Max(value = 65535, message = "SSH 端口范围 1-65535")
    private Integer sshPort;

    @NotBlank(message = "SSH 用户不能为空")
    @Size(max = 64)
    private String sshUser;

    /** Update 留空 = 保留原值. */
    @Size(max = 255)
    private String sshPassword;

    @NotNull(message = "SSH 握手超时不能为空")
    @Min(value = 5) @Max(value = 300)
    private Integer sshTimeoutSeconds;

    @NotNull(message = "SSH 单条命令超时不能为空")
    @Min(value = 5) @Max(value = 300)
    private Integer sshOpTimeoutSeconds;

    @NotNull(message = "SCP 上传超时不能为空")
    @Min(value = 5) @Max(value = 600)
    private Integer sshUploadTimeoutSeconds;

    @NotNull(message = "安装超时不能为空")
    @Min(value = 60) @Max(value = 3600)
    private Integer installTimeoutSeconds;

    @NotNull(message = "带宽峰值不能为空")
    @Min(value = 0)
    private Integer bandwidthMbps;

    /** 线路机域名 (LIVE 前置必填); 不填可空, 上线时校验. */
    @Size(max = 128)
    private String domain;

    /** Cloudflare Zone ID. */
    @Size(max = 64)
    private String cfZoneId;

    /** Cloudflare DNS record ID (创建/绑定后回填). */
    @Size(max = 64)
    private String cfRecordId;

    /** 月度成本 USD. */
    private BigDecimal costMonthlyUsd;

    @Min(value = 1, message = "账单日 1-28")
    @Max(value = 28, message = "账单日 1-28")
    private Integer billingCycleDay;

    /** 服务器到期日 yyyy-MM-dd. */
    private LocalDate expiresAt;

    @NotNull(message = "客户数上限不能为空")
    @Min(value = 1, message = "客户数上限至少 1")
    @Max(value = 10000, message = "客户数上限不能超过 10000")
    private Integer maxConcurrentClients;

    @Size(max = 64)
    private String idcProvider;

    @NotBlank(message = "区域不能为空")
    @Size(max = 32, message = "区域码长度不能超过 32")
    @Pattern(regexp = "^[A-Z][A-Z0-9\\-]+$", message = "区域码须大写, e.g., JP-TYO / US-LAX / HK")
    private String region;

    /** 装机生命周期; 创建时一般传 INSTALLING, 上线由专门接口流转. */
    @NotBlank(message = "lifecycle_state 不能为空")
    @Pattern(regexp = "INSTALLING|READY|LIVE|RETIRED", message = "lifecycleState 须为 INSTALLING/READY/LIVE/RETIRED")
    private String lifecycleState;

    @Size(max = 512)
    private String remark;
}
