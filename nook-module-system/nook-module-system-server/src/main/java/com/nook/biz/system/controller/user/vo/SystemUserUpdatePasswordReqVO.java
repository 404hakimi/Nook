package com.nook.biz.system.controller.user.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 后台用户重置密码 Request VO
 *
 * @author nook
 */
@Data
public class SystemUserUpdatePasswordReqVO {

    /** 新密码; 强度由 Validator 统一校验, 此处只做长度上下界. */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
    private String newPassword;
}
