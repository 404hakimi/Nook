package com.nook.biz.system.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.system.api.enums.SystemUserRoleEnum;
import com.nook.biz.system.api.enums.SystemUserStatusEnum;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 后台用户 DO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_user")
public class SystemUser extends BaseEntity {

    /** 登录用户名; 唯一. */
    private String username;

    /** BCrypt 密码哈希. */
    private String passwordHash;

    /** 真实姓名. */
    private String realName;

    /** 邮箱; 唯一 (可空). */
    private String email;

    /** 角色 {@link SystemUserRoleEnum} */
    private String role;

    /** 状态 {@link SystemUserStatusEnum} */
    private Integer status;

    /** 最近登录时间. */
    private LocalDateTime lastLoginAt;

    /** 最近登录 IP. */
    private String lastLoginIp;

    /** 备注. */
    private String remark;

    /** 软删除标记: 0=未删 1=已删. */
    @TableLogic
    private Integer deleted;
}
