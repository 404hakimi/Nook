package com.nook.biz.member.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.biz.member.api.enums.MemberUserStatusEnum;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会员 DO
 *
 * @author nook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("member_user")
public class MemberUser extends BaseEntity {

    /** 邮箱; 唯一, 兼任登录账号. */
    private String email;

    /** BCrypt 密码哈希. */
    private String passwordHash;

    /** 订阅 URL token, 32 位 hex, 全局唯一. */
    private String subToken;

    /** 状态 {@link MemberUserStatusEnum} */
    private Integer status;

    /** 最近登录时间. */
    private LocalDateTime lastLoginAt;

    /** 最近登录 IP. */
    private String lastLoginIp;

    /** 管理员备注. */
    private String remark;

    /** 软删除标记: 0=未删 1=已删. */
    @TableLogic
    private Integer deleted;
}
