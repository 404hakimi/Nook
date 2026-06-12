package com.nook.biz.member.controller.portal.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.member.api.enums.MemberUserStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户端 - 会员 Response VO
 *
 * @author nook
 */
@Data
public class PortalMemberRespVO {

    /** 邮箱. */
    private String email;

    /** 订阅 URL token; 拼入订阅地址供客户端导入. */
    private String subToken;

    /** 状态 {@link MemberUserStatusEnum} */
    private Integer status;

    /** 最近登录时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;

    /** 注册时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
