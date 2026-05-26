package com.nook.biz.node.controller.resource.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - SOCKS5 落地节点 SSH 凭据更新 Request VO
 *
 * @author nook
 */
@Data
public class ServerLandingCredentialUpdateReqVO {

    // SSH 主机 = resource_server.ip_address (canonical); 改主机走核心字段编辑 (updateCore)

    @Min(value = 1) @Max(value = 65535)
    private Integer sshPort;

    @Size(max = 64)
    private String sshUser;

    /** 留空 = 保留原值. */
    @Size(max = 128)
    private String sshPassword;
}
