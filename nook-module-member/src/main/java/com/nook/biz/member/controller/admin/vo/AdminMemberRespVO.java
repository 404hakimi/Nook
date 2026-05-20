package com.nook.biz.member.controller.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** Admin 端会员展示视图; 含管理字段 (last_login_ip / remark). 同样不返回 passwordHash. */
@Data
public class AdminMemberRespVO {

    private String id;
    private String email;
    private String subToken;

    /** 状态: 1=正常 2=禁用 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
