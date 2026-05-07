package com.nook.biz.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 更新后台用户入参；用户名与密码不在此处改，密码走 reset 接口。 */
@Data
public class UpdateSystemUserDTO {

    @Size(max = 64, message = "真实姓名不能超过 64 字")
    private String realName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    @Pattern(regexp = "super_admin|operator|devops", message = "角色必须是 super_admin / operator / devops")
    private String role;

    /** 1=正常 2=禁用 */
    private Integer status;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
