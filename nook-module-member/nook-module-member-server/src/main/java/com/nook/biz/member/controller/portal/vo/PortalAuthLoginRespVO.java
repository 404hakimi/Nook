package com.nook.biz.member.controller.portal.vo;

import lombok.Data;

/**
 * 客户端 - 会员登录 Response VO
 *
 * @author nook
 */
@Data
public class PortalAuthLoginRespVO {

    /** 会话 token. */
    private String token;

    /** token 剩余有效期 (秒). */
    private long expiresIn;

    /** 会员信息. */
    private PortalMemberRespVO member;
}
