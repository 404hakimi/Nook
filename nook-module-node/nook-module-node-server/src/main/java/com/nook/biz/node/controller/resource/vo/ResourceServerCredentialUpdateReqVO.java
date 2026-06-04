package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 注: 主机地址在服务器主表, 不在凭据里

/**
 * 管理后台 - 服务器 SSH 凭据 Update Request VO
 *
 * @author nook
 */
@Data
public class ResourceServerCredentialUpdateReqVO {

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
}
