package com.nook.biz.system.controller.user.vo;

import com.nook.biz.system.api.enums.SystemUserRoleEnum;
import com.nook.biz.system.api.enums.SystemUserStatusEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 后台用户创建 Request VO
 *
 * @author nook
 */
@Data
public class SystemUserCreateReqVO {

    /** 登录用户名. */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$", message = "用户名必须是 3-32 位字母数字下划线")
    private String username;

    /** 密码; 强度由 Validator 统一校验, 此处只做长度上下界. */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
    private String password;

    /** 真实姓名. */
    @Size(max = 64, message = "真实姓名不能超过 64 字")
    private String realName;

    /** 邮箱. */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    /** 角色 {@link SystemUserRoleEnum}; 取值由 Validator 校验. */
    @NotBlank(message = "角色不能为空")
    private String role;

    /** 状态 {@link SystemUserStatusEnum}; 不传默认正常. */
    private Integer status;

    /** 备注. */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
