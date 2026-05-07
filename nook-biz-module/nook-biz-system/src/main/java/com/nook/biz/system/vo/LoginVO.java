package com.nook.biz.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录成功响应：token + 当前用户基础信息。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    private String token;

    private long expiresIn;

    private SystemUserVO user;
}
