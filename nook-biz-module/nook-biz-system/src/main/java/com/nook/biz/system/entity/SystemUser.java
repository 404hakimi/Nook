package com.nook.biz.system.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nook.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 后台系统用户(管理员/运营/运维)。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_user")
public class SystemUser extends BaseEntity {

    private String username;

    private String passwordHash;

    private String realName;

    private String email;

    private String phone;

    /** super_admin / operator / devops */
    private String role;

    /** 1=正常 2=禁用 */
    private Integer status;

    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    private String remark;

    @TableLogic
    private Integer deleted;
}
