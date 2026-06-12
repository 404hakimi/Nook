package com.nook.biz.system.controller.user.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.system.api.enums.SystemUserRoleEnum;
import com.nook.biz.system.api.enums.SystemUserStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 后台用户 Response VO
 *
 * @author nook
 */
@Data
public class SystemUserRespVO {

    /** 用户ID. */
    private String id;

    /** 登录用户名. */
    private String username;

    /** 真实姓名. */
    private String realName;

    /** 邮箱. */
    private String email;

    /** 角色 {@link SystemUserRoleEnum} */
    private String role;

    /** 状态 {@link SystemUserStatusEnum} */
    private Integer status;

    /** 最近登录时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;

    /** 最近登录 IP. */
    private String lastLoginIp;

    /** 备注. */
    private String remark;

    /** 创建时间. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
