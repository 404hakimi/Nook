package com.nook.biz.system.controller.user.vo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增后台用户入参; 业务校验 (用户名唯一 / 邮箱唯一) 由 SystemUserValidator 完成.
 *
 * @author nook
 */
@Data
public class SystemUserCreateReqVO {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$", message = "用户名必须是 3-32 位字母数字下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
    private String password;

    @Size(max = 64, message = "真实姓名不能超过 64 字")
    private String realName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "super_admin|operator|devops", message = "角色必须是 super_admin / operator / devops")
    private String role;

    /** 1=正常 2=禁用; 不传默认 1 (Service 兜底). */
    @Min(value = 1) @Max(value = 2)
    private Integer status;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
