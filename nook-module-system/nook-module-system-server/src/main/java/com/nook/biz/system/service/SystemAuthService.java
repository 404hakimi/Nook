package com.nook.biz.system.service;

import com.nook.biz.system.controller.auth.vo.AuthLoginReqVO;
import com.nook.biz.system.controller.auth.vo.AuthLoginRespVO;
import com.nook.biz.system.entity.SystemUser;

/**
 * 后台认证 Service 接口
 *
 * @author nook
 */
public interface SystemAuthService {

    /**
     * 用户名 + 密码登录
     *
     * @param reqVO    登录信息
     * @param clientIp 客户端 IP
     * @return token + 当前用户信息
     */
    AuthLoginRespVO login(AuthLoginReqVO reqVO, String clientIp);

    /**
     * 登出当前 token (幂等)
     */
    void logout();

    /**
     * 获得当前登录用户
     *
     * @return 用户信息
     */
    SystemUser getLoginUser();
}
