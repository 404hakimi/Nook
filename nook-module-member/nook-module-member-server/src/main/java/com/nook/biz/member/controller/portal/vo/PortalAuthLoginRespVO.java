package com.nook.biz.member.controller.portal.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户端 - 会员登录 Response VO
 *
 * @author nook
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortalAuthLoginRespVO {

    private String token;

    private long expiresIn;

    private PortalMemberRespVO member;
}
