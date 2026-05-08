package com.nook.biz.resource.controller.server.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务器详情/列表响应。
 * SSH 密码/私钥不会下发给前端, 仅以 sshAuthConfigured(boolean) 表示"是否已配置".
 * 编辑时密码字段留空 = 保留旧值.
 */
@Data
public class ResourceServerRespVO {

    private String id;
    private String name;
    private String host;
    private Integer sshPort;
    private String sshUser;
    private Boolean sshAuthConfigured;
    private Integer sshTimeoutSeconds;

    private Integer backendTimeoutSeconds;

    private String xrayGrpcHost;
    private Integer xrayGrpcPort;

    private Integer totalBandwidth;
    private Integer monthlyTrafficGb;
    private Integer totalIpCount;
    private String idcProvider;
    private String region;

    private Integer status;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
