package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 服务器 SSH 凭据更新 Request VO
 *
 * @author nook
 */
@Data
public class ResourceServerCredentialUpdateReqVO {

    /** SSH 端口. */
    @NotNull(message = "SSH 端口不能为空")
    @Min(value = 1, message = "SSH 端口范围 1-65535")
    @Max(value = 65535, message = "SSH 端口范围 1-65535")
    private Integer sshPort;

    /** SSH 用户. */
    @NotBlank(message = "SSH 用户不能为空")
    @Size(max = 64)
    private String sshUser;

    /** SSH 密码; 留空 = 保留原值. */
    @Size(max = 255)
    private String sshPassword;

    /** SSH 握手超时 (秒). */
    @NotNull(message = "SSH 握手超时不能为空")
    @Min(value = 5) @Max(value = 300)
    private Integer sshTimeoutSeconds;

    /** SSH 单条命令超时 (秒). */
    @NotNull(message = "SSH 单条命令超时不能为空")
    @Min(value = 5) @Max(value = 300)
    private Integer sshOpTimeoutSeconds;

    /** SCP 上传超时 (秒). */
    @NotNull(message = "SCP 上传超时不能为空")
    @Min(value = 5) @Max(value = 600)
    private Integer sshUploadTimeoutSeconds;

    /** 装机整体超时 (秒). */
    @NotNull(message = "安装超时不能为空")
    @Min(value = 60) @Max(value = 3600)
    private Integer installTimeoutSeconds;
}
