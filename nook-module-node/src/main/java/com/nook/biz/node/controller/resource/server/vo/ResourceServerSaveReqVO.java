package com.nook.biz.node.controller.resource.server.vo;

import com.nook.biz.node.enums.ResourceServerStatusEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

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

    /** Update 留空 = 保留原值, 故不加 @NotBlank. */
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

    @Min(value = 0) @Max(value = 1_048_576)
    private Integer monthlyTrafficGb;

    @NotNull(message = "带宽峰值不能为空")
    @Min(value = 0)
    private Integer totalBandwidth;

    @Size(max = 64)
    private String idcProvider;

    @Size(max = 64)
    private String region;

    /** 状态; 取值见 {@link ResourceServerStatusEnum} */
    @NotNull(message = "状态不能为空")
    @Min(value = 1, message = "状态值越界")
    @Max(value = 3, message = "状态值越界")
    private Integer status;

    @Size(max = 512)
    private String remark;
}
