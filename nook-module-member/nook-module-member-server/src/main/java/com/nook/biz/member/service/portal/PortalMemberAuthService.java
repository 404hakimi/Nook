package com.nook.biz.member.service.portal;

import com.nook.biz.member.controller.portal.vo.PortalAuthLoginRespVO;
import com.nook.biz.member.controller.portal.vo.PortalLoginReqVO;
import com.nook.biz.member.controller.portal.vo.PortalRegisterReqVO;

/**
 * 客户端 - 会员认证 Service 接口
 *
 * @author nook
 */
public interface PortalMemberAuthService {

    /**
     * 注册新会员 (仅建账号, 不登录)
     *
     * @param reqVO 注册信息
     */
    void registerMember(PortalRegisterReqVO reqVO);

    /**
     * 邮箱 + 密码登录
     *
     * @param reqVO    登录信息
     * @param clientIp 客户端 IP
     * @return token + 会员信息
     */
    PortalAuthLoginRespVO login(PortalLoginReqVO reqVO, String clientIp);
}
