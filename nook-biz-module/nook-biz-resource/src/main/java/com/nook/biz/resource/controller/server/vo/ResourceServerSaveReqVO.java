package com.nook.biz.resource.controller.server.vo;

import com.nook.common.web.validation.Create;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 服务器新增/编辑统一入参。
 * - Create 场景必填: name / host / backendType；其它字段视 backendType 在 service 层校验
 * - Update 场景所有字段可选(传啥改啥)
 *
 * 凭据字段(panelPassword / sshPassword / sshPrivateKey)在 update 时如果传空串视为"清空"，
 * 传 null/不传视为"保持原值不变"——前端 UI 应对 password 字段提供"留空保留旧值"的提示。
 */
@Data
public class ResourceServerSaveReqVO {

    @NotBlank(message = "服务器别名不能为空", groups = Create.class)
    @Size(max = 64)
    private String name;

    @NotBlank(message = "管理 IP/域名不能为空", groups = Create.class)
    @Size(max = 128)
    private String host;

    private Integer sshPort;

    @Size(max = 64)
    private String sshUser;

    @Size(max = 255)
    private String sshPassword;

    private String sshPrivateKey;

    /**
     * SSH 命令最大耗时(秒)。Create 时必填；Update 时可空表示不改。
     * 5-300 范围：太小经常超时(x-ui status / journalctl 在远端动辄 10s+)，太大会让前端长时间挂起。
     */
    @NotNull(message = "SSH 超时不能为空", groups = Create.class)
    @Min(value = 5, message = "SSH 超时最小 5 秒")
    @Max(value = 300, message = "SSH 超时最大 300 秒")
    private Integer sshTimeoutSeconds;

    @NotBlank(message = "backendType 不能为空", groups = Create.class)
    @Pattern(regexp = "threexui|xray-grpc", message = "backendType 只能是 threexui 或 xray-grpc")
    private String backendType;

    @Size(max = 255)
    private String panelBaseUrl;

    @Size(max = 64)
    private String panelUsername;

    @Size(max = 255)
    private String panelPassword;

    /** 0=否 1=是 */
    private Integer panelIgnoreTls;

    /**
     * backend HTTP/gRPC 调用超时(秒)。Create 必填；Update 可空表示不改。
     * 5-120 范围：跨洲 HTTPS+TLS 握手 + login + listInbounds 一连串，建议 20-60。
     */
    @NotNull(message = "Backend 超时不能为空", groups = Create.class)
    @Min(value = 5, message = "Backend 超时最小 5 秒")
    @Max(value = 120, message = "Backend 超时最大 120 秒")
    private Integer backendTimeoutSeconds;

    /**
     * 月流量额度 GB；可选（部分 VPS 套餐不限流量）。
     * 上限 1024TB = 1048576 GB，超出认为是误填。
     */
    @Min(value = 0, message = "月流量额度不能为负")
    @Max(value = 1048576, message = "月流量额度上限 1024TB")
    private Integer monthlyTrafficGb;

    @Size(max = 128)
    private String xrayGrpcHost;

    private Integer xrayGrpcPort;

    private Integer totalBandwidth;

    @Size(max = 64)
    private String idcProvider;

    @Size(max = 64)
    private String region;

    /** 1=运行 2=维护 3=下线 */
    private Integer status;

    @Size(max = 512)
    private String remark;
}
