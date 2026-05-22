package com.nook.biz.member.controller.portal.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 注册 / 登录成功响应: token + 会员基础信息. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortalAuthLoginRespVO {

    private String token;

    private long expiresIn;

    private PortalMemberRespVO member;
}
