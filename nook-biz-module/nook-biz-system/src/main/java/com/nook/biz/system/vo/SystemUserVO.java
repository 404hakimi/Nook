package com.nook.biz.system.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nook.biz.system.entity.SystemUser;
import lombok.Data;

import java.time.LocalDateTime;

/** 后台用户对外展示视图，剔除密码哈希等敏感字段。 */
@Data
public class SystemUserVO {

    private String id;
    private String username;
    private String realName;
    private String email;
    private String role;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 从实体转 VO，password_hash 等字段不会暴露。 */
    public static SystemUserVO from(SystemUser e) {
        if (e == null) {
            return null;
        }
        SystemUserVO v = new SystemUserVO();
        v.id = e.getId();
        v.username = e.getUsername();
        v.realName = e.getRealName();
        v.email = e.getEmail();
        v.role = e.getRole();
        v.status = e.getStatus();
        v.lastLoginAt = e.getLastLoginAt();
        v.lastLoginIp = e.getLastLoginIp();
        v.remark = e.getRemark();
        v.createdAt = e.getCreatedAt();
        return v;
    }
}
