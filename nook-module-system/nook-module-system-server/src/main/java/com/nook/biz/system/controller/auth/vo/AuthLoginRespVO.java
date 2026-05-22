package com.nook.biz.system.controller.auth.vo;

import com.nook.biz.system.controller.user.vo.SystemUserRespVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录成功响应：token + 当前用户基础信息。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthLoginRespVO {

    private String token;

    private long expiresIn;

    private SystemUserRespVO user;
}
