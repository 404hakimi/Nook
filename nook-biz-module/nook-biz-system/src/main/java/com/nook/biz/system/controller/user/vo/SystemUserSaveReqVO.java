package com.nook.biz.system.controller.user.vo;

import com.nook.common.web.validation.Create;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增/编辑后台用户的统一入参。
 * 由 @Validated(Create.class) / @Validated(Update.class) 区分校验规则:
 *   - 新增: username / password / role 必填
 *   - 编辑: 仅校验字段格式; username/password 在编辑时被 Service 忽略(密码走 update-password 接口)
 */
@Data
public class SystemUserSaveReqVO {

    @NotBlank(message = "用户名不能为空", groups = Create.class)
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$", message = "用户名必须是 3-32 位字母数字下划线", groups = Create.class)
    private String username;

    @NotBlank(message = "密码不能为空", groups = Create.class)
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间", groups = Create.class)
    private String password;

    @Size(max = 64, message = "真实姓名不能超过 64 字")
    private String realName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    @NotBlank(message = "角色不能为空", groups = Create.class)
    @Pattern(regexp = "super_admin|operator|devops", message = "角色必须是 super_admin / operator / devops")
    private String role;

    /** 1=正常 2=禁用 */
    private Integer status;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
