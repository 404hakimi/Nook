package com.nook.biz.node.controller.resource.vo;

import com.nook.biz.node.enums.ResourceIpPoolProvisionModeEnum;
import com.nook.biz.node.enums.ResourceIpPoolStatusEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * IP 池新增 / 编辑 Request VO.
 *
 * @author nook
 */
@Data
public class ResourceIpPoolSaveReqVO {

    @NotBlank(message = "区域不能为空")
    @Size(max = 64)
    private String region;

    @NotBlank(message = "IP 类型不能为空")
    @Size(max = 36)
    private String ipTypeId;

    /** 部署模式; 取值见 {@link ResourceIpPoolProvisionModeEnum} */
    @NotNull(message = "部署模式不能为空")
    @Min(value = 1, message = "部署模式值越界")
    @Max(value = 2, message = "部署模式值越界")
    private Integer provisionMode;

    /** 出网真实 IP, 同时作为 SOCKS5 监听地址 */
    @NotBlank(message = "IP 地址不能为空")
    @Size(max = 45)
    private String ipAddress;

    @NotNull(message = "SOCKS5 端口不能为空")
    @Min(value = 1) @Max(value = 65535)
    private Integer socks5Port;

    @Size(max = 64)
    private String socks5Username;

    /** Update 留空 = 保留原值, 故不加 @NotBlank. */
    @Size(max = 255)
    private String socks5Password;

    /** 状态; 取值见 {@link ResourceIpPoolStatusEnum} */
    @NotNull(message = "状态不能为空")
    @Min(value = 1, message = "状态值越界")
    @Max(value = 6, message = "状态值越界")
    private Integer status;

    /** dante 日志关键字组合 (空格分隔); 默认 'connect disconnect error'. */
    @Size(max = 64)
    private String logLevel;

    /** dante logoutput 路径; 默认 /var/log/sockd.log. */
    @Size(max = 255)
    private String logPath;

    /** systemd 开机自启 (1=enable 0=disable); 默认 1. */
    @Min(value = 0) @Max(value = 1)
    private Integer autostartEnabled;

    /** 部署时是否配 UFW (1=配置 0=跳过); 默认 1. */
    @Min(value = 0) @Max(value = 1)
    private Integer firewallEnabled;

    /** UFW allow 来源 CIDR; 空 = 0.0.0.0/0. */
    @Size(max = 255)
    private String firewallAllowFrom;

    /** SOCKS5 安装目录; 默认 /home/socks5. logs/info 等放这里; 留空走默认. */
    @Size(max = 255)
    private String installDir;

    /** SSH 主机; 留空则用 ipAddress (出网 IP) 作为兜底. */
    @Size(max = 128)
    private String sshHost;

    /** SSH 端口; 留空默认 22. */
    @Min(value = 1) @Max(value = 65535)
    private Integer sshPort;

    /** SSH 用户. */
    @Size(max = 64)
    private String sshUser;

    /** SSH 密码; Update 留空 = 保留原值, 故不加 @NotBlank. */
    @Size(max = 255)
    private String sshPassword;

    /** 采购带宽上限 (Mbps); NULL = 不限/未填. 仅账面记录, 不参与运行时分配. */
    @Min(value = 1) @Max(value = 1_000_000)
    private Integer bandwidthMbps;

    /** 采购流量上限 (GB); NULL = 不限/未填. 仅账面记录. */
    @Min(value = 1) @Max(value = 10_000_000)
    private Integer trafficQuotaGb;

    @Size(max = 255)
    private String remark;
}
