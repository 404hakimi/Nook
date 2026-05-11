package com.nook.biz.system.controller.user.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 后台用户对外展示视图，剔除密码哈希等敏感字段。entity → vo 由 SystemUserConvert 统一处理。 */
@Data
public class SystemUserRespVO {

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
}
