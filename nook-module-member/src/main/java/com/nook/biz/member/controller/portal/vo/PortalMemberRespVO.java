package com.nook.biz.member.controller.portal.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 会员自查信息出参; 不返回 passwordHash / deleted 等敏感字段. */
@Data
public class PortalMemberRespVO {

    private String id;
    private String email;

    /** 用户级聚合订阅 URL token; 用户拼接 https://<host>/portal/sub/{subToken} 拿订阅. */
    private String subToken;

    /** 状态: 1=正常 2=禁用 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
