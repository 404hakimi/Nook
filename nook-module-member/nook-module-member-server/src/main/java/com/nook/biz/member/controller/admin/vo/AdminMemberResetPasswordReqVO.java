package com.nook.biz.member.controller.admin.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理后台 - 重置会员密码 Request VO
 *
 * @author nook
 */
@Data
public class AdminMemberResetPasswordReqVO {

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
    private String password;
}
