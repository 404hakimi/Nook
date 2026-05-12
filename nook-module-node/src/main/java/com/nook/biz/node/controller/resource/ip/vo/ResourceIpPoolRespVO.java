package com.nook.biz.node.controller.resource.ip.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.node.enums.ResourceIpPoolProvisionModeEnum;
import com.nook.biz.node.enums.ResourceIpPoolStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IP 池列表 / 详情 Response VO.
 *
 * @author nook
 */
@Data
public class ResourceIpPoolRespVO {

    private String id;
    private String region;
    private String ipTypeId;

    /** 部署模式; 取值见 {@link ResourceIpPoolProvisionModeEnum} */
    private Integer provisionMode;

    /** 出网真实 IP, 同时作为 SOCKS5 监听地址 */
    private String ipAddress;

    private Integer socks5Port;
    private String socks5Username;

    /** 明文 SOCKS5 密码; 后台运营受信网络使用, UI 用 type=password 自然遮盖. */
    private String socks5Password;

    /** 状态; 取值见 {@link ResourceIpPoolStatusEnum} */
    private Integer status;

    private String assignedMemberId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime assignedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime coolingUntil;

    private Integer assignCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastHealthAt;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
