package com.nook.biz.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新增后台用户入参。 */
@Data
public class CreateSystemUserDTO {

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

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
