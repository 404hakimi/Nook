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

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
    private String newPassword;
}
