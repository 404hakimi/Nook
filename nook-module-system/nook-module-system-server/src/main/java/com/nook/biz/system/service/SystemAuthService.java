package com.nook.biz.system.service;

import com.nook.biz.system.controller.auth.vo.AuthLoginReqVO;
import com.nook.biz.system.controller.auth.vo.AuthLoginRespVO;

/**
 * 后台认证 Service 接口
 *
 * @author nook
 */
public interface SystemAuthService {

    /** 校验用户名密码，签发 sa-token；失败抛 BusinessException。 */
    AuthLoginRespVO login(AuthLoginReqVO reqVO, String clientIp);

    /** 注销当前 token；幂等。 */
    void logout();
}
