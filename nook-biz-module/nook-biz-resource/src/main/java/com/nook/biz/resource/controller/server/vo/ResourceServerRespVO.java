package com.nook.biz.resource.controller.server.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务器详情/列表响应。
 * <strong>密码相关字段不会下发给前端</strong>——下发的字段里只有"是否已配置"的布尔标志(panelPasswordConfigured 等)，
 * 前端在编辑时密码留空意味着"保留旧值"。
 */
@Data
public class ResourceServerRespVO {

    private String id;
    private String name;
    private String host;
    private Integer sshPort;
    private String sshUser;
    /** 是否已配置 SSH 密码或私钥(布尔)；具体值不下发 */
    private Boolean sshAuthConfigured;
    private Integer sshTimeoutSeconds;

    private String backendType;
    private String panelBaseUrl;
    private String panelUsername;
    /** 是否已配置面板密码 */
    private Boolean panelPasswordConfigured;
    private Integer panelIgnoreTls;
    private Integer backendTimeoutSeconds;

    private String xrayGrpcHost;
    private Integer xrayGrpcPort;

    private Integer totalBandwidth;
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
