package com.nook.biz.member.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.framework.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 会员用户 (Nook 客户端账户; 跟后台 system_user 体系隔离). */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("member_user")
public class MemberUser extends BaseEntity {

    /** 邮箱; 唯一, 兼任登录账号. */
    private String email;

    /** BCrypt 密码哈希. */
    private String passwordHash;

    /** 用户级聚合订阅 URL token, 32 char hex; 全局唯一; portal 端 GET /portal/sub/{sub_token} 用. */
    private String subToken;

    /** 状态: 1=正常 2=禁用 */
    private Integer status;

    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    private String remark;

    @TableLogic
    private Integer deleted;
}
