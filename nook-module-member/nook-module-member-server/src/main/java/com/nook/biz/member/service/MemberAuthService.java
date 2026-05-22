package com.nook.biz.member.service;

import com.nook.biz.member.controller.portal.vo.PortalAuthLoginRespVO;
import com.nook.biz.member.controller.portal.vo.PortalLoginReqVO;
import com.nook.biz.member.controller.portal.vo.PortalRegisterReqVO;

/**
 * 会员认证 Service 接口
 *
 * @author nook
 */
public interface MemberAuthService {

    /**
     * 注册新会员; 注册成功后自动登录, 返回 token + 会员信息.
     *
     * @param reqVO    入参
     * @param clientIp 客户端 IP (写 last_login_ip)
     * @return token + 会员信息
     */
    PortalAuthLoginRespVO register(PortalRegisterReqVO reqVO, String clientIp);

    /**
     * 邮箱 + 密码登录.
     *
     * @param reqVO    入参
     * @param clientIp 客户端 IP
     * @return token + 会员信息
     */
    PortalAuthLoginRespVO login(PortalLoginReqVO reqVO, String clientIp);

    /** 登出当前 token; 幂等. */
    void logout();
}
