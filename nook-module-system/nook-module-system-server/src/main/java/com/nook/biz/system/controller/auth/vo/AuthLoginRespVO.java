package com.nook.biz.system.controller.auth.vo;

import com.nook.biz.system.controller.user.vo.SystemUserRespVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理后台 - 后台登录 Response VO
 *
 * @author nook
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthLoginRespVO {

    private String token;

    private long expiresIn;

    private SystemUserRespVO user;
}
