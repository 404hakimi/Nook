package com.nook.biz.system.controller.user.vo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑后台用户入参; username/password 不在此修改 (密码走 PUT /{id}/password); 字段为 null = 保留原值.
 *
 * @author nook
 */
@Data
public class SystemUserUpdateReqVO {

    @Size(max = 64, message = "真实姓名不能超过 64 字")
    private String realName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    @Pattern(regexp = "super_admin|operator|devops", message = "角色必须是 super_admin / operator / devops")
    private String role;

    /** 1=正常 2=禁用. */
    @Min(value = 1) @Max(value = 2)
    private Integer status;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
