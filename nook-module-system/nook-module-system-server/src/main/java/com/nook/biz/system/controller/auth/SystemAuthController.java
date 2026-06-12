package com.nook.biz.system.controller.auth;

import com.nook.biz.system.controller.auth.vo.AuthLoginReqVO;
import com.nook.biz.system.controller.auth.vo.AuthLoginRespVO;
import com.nook.biz.system.controller.user.vo.SystemUserRespVO;
import com.nook.biz.system.convert.user.SystemUserConvert;
import com.nook.biz.system.entity.SystemUser;
import com.nook.biz.system.service.SystemAuthService;
import com.nook.common.web.response.Result;
import com.nook.framework.web.ClientIpResolver;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 后台认证 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/system/auth")
@Validated
public class SystemAuthController {

    @Resource
    private SystemAuthService systemAuthService;

    /**
     * 用户名 + 密码登录
     *
     * @param reqVO   登录信息
     * @param httpReq HTTP 请求 (取客户端 IP)
     * @return token + 当前用户信息
     */
    @PostMapping("/login")
    public Result<AuthLoginRespVO> login(@RequestBody @Valid AuthLoginReqVO reqVO,
                                         HttpServletRequest httpReq) {
        // 解析客户端 IP
        String clientIp = ClientIpResolver.resolve(httpReq);
        // 登录, 返回 token + 用户信息
        AuthLoginRespVO respVO = systemAuthService.login(reqVO, clientIp);
        return Result.ok(respVO);
    }

    /**
     * 登出当前 token; 幂等
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        systemAuthService.logout();
        return Result.ok();
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 用户信息
     */
    @GetMapping("/me")
    public Result<SystemUserRespVO> me() {
        // 查询当前登录用户
        SystemUser user = systemAuthService.getLoginUser();
        // 转换返回
        return Result.ok(SystemUserConvert.INSTANCE.convert(user));
    }
}
