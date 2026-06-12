package com.nook.biz.system.controller.user.vo;

import com.nook.biz.system.api.enums.SystemUserRoleEnum;
import com.nook.biz.system.api.enums.SystemUserStatusEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 后台用户更新 Request VO
 *
 * @author nook
 */
@Data
public class SystemUserUpdateReqVO {

    /** 真实姓名. */
    @Size(max = 64, message = "真实姓名不能超过 64 字")
    private String realName;

    /** 邮箱. */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    /** 角色 {@link SystemUserRoleEnum}; 取值由 Validator 校验; null=不修改. */
    private String role;

    /** 状态 {@link SystemUserStatusEnum}; null=不修改. */
    private Integer status;

    /** 备注. */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
