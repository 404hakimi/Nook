package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - IP 池 SSH 凭据 Update Request VO (Create 流程嵌套在 SaveReqVO 内复用)
 *
 * @author nook
 */
@Data
public class ResourceIpPoolCredentialUpdateReqVO {

    /** 留空 = 用 ip_address 兜底. */
    @Size(max = 128)
    private String sshHost;

    /** 留空 = 22. */
    @Min(value = 1, message = "SSH 端口范围 1-65535")
    @Max(value = 65535, message = "SSH 端口范围 1-65535")
    private Integer sshPort;

    /** 留空 = root. */
    @Size(max = 64)
    private String sshUser;

    /** Update 留空 = 保留原值. */
    @Size(max = 255)
    private String sshPassword;
}
