package com.nook.biz.member.controller.portal.vo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客户端 - 会员登录 Request VO
 *
 * @author nook
 */
@Data
public class PortalLoginReqVO {

    /** 登录邮箱. */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    /** 登录密码. */
    @NotBlank(message = "密码不能为空")
    @Size(min = 1, max = 64, message = "密码长度需在 1-64 之间")
    private String password;
}
