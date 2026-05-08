package com.nook.biz.resource.controller.server.vo;

import com.nook.common.web.validation.Create;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 服务器新增 / 编辑统一入参; 字段值完全由前端传入, 后端不再补默认值。
 *
 * <p>Create 必填: name / host / sshPort / sshUser / sshTimeoutSeconds / backendTimeoutSeconds /
 *                 totalBandwidth / status / xrayGrpcHost / xrayGrpcPort / SSH 凭据(密码或私钥)。
 * <p>Update: 全部字段可空, 传啥改啥; 凭据字段(sshPassword/sshPrivateKey) 留空 = 保留原值, 传非空 = 覆盖。
 */
@Data
public class ResourceServerSaveReqVO {

    @NotBlank(message = "服务器别名不能为空", groups = Create.class)
    @Size(max = 64)
    private String name;

    @NotBlank(message = "管理 IP/域名不能为空", groups = Create.class)
    @Size(max = 128)
    private String host;

    @NotNull(message = "SSH 端口不能为空", groups = Create.class)
    @Min(value = 1, message = "SSH 端口最小 1")
    @Max(value = 65535, message = "SSH 端口最大 65535")
    private Integer sshPort;

    @NotBlank(message = "SSH 用户不能为空", groups = Create.class)
    @Size(max = 64)
    private String sshUser;

    @Size(max = 255)
    private String sshPassword;

    private String sshPrivateKey;

    /** 5-300s; 太小 x-ui 状态 / journalctl 经常超时, 太大前端会长时间挂起。 */
    @NotNull(message = "SSH 超时不能为空", groups = Create.class)
    @Min(value = 5, message = "SSH 超时最小 5 秒")
    @Max(value = 300, message = "SSH 超时最大 300 秒")
    private Integer sshTimeoutSeconds;

    /** 5-120s; 跨洲网络 20-60s 比较合理。 */
    @NotNull(message = "Backend 超时不能为空", groups = Create.class)
    @Min(value = 5, message = "Backend 超时最小 5 秒")
    @Max(value = 120, message = "Backend 超时最大 120 秒")
    private Integer backendTimeoutSeconds;

    /** 月流量额度 GB; 可空表示不限流量套餐, 上限 1024TB 防误填。 */
    @Min(value = 0, message = "月流量额度不能为负")
    @Max(value = 1048576, message = "月流量额度上限 1024TB")
    private Integer monthlyTrafficGb;

    @NotBlank(message = "Xray gRPC 主机不能为空", groups = Create.class)
    @Size(max = 128)
    private String xrayGrpcHost;

    @NotNull(message = "Xray gRPC 端口不能为空", groups = Create.class)
    @Min(value = 1, message = "Xray gRPC 端口最小 1")
    @Max(value = 65535, message = "Xray gRPC 端口最大 65535")
    private Integer xrayGrpcPort;

    @NotNull(message = "带宽峰值不能为空", groups = Create.class)
    @Min(value = 0, message = "带宽峰值不能为负")
    private Integer totalBandwidth;

    @Size(max = 64)
    private String idcProvider;

    @Size(max = 64)
    private String region;

    /** 1=运行 2=维护 3=下线 */
    @NotNull(message = "状态不能为空", groups = Create.class)
    @Min(value = 1) @Max(value = 3)
    private Integer status;

    @Size(max = 512)
    private String remark;
}
