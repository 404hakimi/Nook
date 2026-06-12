package com.nook.biz.member.controller.portal.vo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客户端 - 会员注册 Request VO
 *
 * @author nook
 */
@Data
public class PortalRegisterReqVO {

    /** 注册邮箱; 兼任登录账号. */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    /** 密码; 强度由 Validator 统一校验, 此处只做长度上下界. */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
    private String password;
}
