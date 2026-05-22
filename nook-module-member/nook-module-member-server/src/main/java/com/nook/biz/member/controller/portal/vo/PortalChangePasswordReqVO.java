package com.nook.biz.member.controller.portal.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客户端 - 会员修改密码 Request VO
 *
 * @author nook
 */
@Data
public class PortalChangePasswordReqVO {

    @NotBlank(message = "原密码不能为空")
    @Size(min = 1, max = 64, message = "原密码长度需在 1-64 之间")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "新密码长度需在 8-64 之间")
    private String newPassword;
}
