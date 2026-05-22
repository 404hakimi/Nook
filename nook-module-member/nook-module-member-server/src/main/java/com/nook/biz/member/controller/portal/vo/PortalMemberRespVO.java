package com.nook.biz.member.controller.portal.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户端 - 会员 Response VO
 *
 * @author nook
 */
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
