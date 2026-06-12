package com.nook.biz.member.controller.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.member.api.enums.MemberUserStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 会员 Response VO
 *
 * @author nook
 */
@Data
public class MemberRespVO {

    /** 会员ID. */
    private String id;

    /** 邮箱. */
    private String email;

    /** 订阅 URL token. */
    private String subToken;

    /** 状态 {@link MemberUserStatusEnum} */
    private Integer status;

    /** 最近登录时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;

    /** 最近登录 IP. */
    private String lastLoginIp;

    /** 管理员备注. */
    private String remark;

    /** 注册时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
