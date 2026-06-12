package com.nook.biz.system.controller.auth.vo;

import com.nook.biz.system.controller.user.vo.SystemUserRespVO;
import lombok.Data;

/**
 * 管理后台 - 后台登录 Response VO
 *
 * @author nook
 */
@Data
public class AuthLoginRespVO {

    /** 会话 token. */
    private String token;

    /** token 剩余有效期 (秒). */
    private long expiresIn;

    /** 当前用户信息. */
    private SystemUserRespVO user;
}
