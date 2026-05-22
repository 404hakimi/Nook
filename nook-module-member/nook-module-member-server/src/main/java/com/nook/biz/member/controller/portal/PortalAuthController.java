package com.nook.biz.member.controller.portal;

import com.nook.biz.member.controller.portal.vo.PortalAuthLoginRespVO;
import com.nook.biz.member.controller.portal.vo.PortalLoginReqVO;
import com.nook.biz.member.controller.portal.vo.PortalRegisterReqVO;
import com.nook.biz.member.service.MemberAuthService;
import com.nook.common.web.response.Result;
import com.nook.framework.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端 - 会员认证 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/portal/member/auth")
@RequiredArgsConstructor
public class PortalAuthController {

    private final MemberAuthService memberAuthService;

    /** 注册新会员; 注册成功即自动登录, 直接返回 token. */
    @PostMapping("/register")
    public Result<PortalAuthLoginRespVO> register(@RequestBody @Valid PortalRegisterReqVO reqVO,
                                                   HttpServletRequest httpReq) {
        return Result.ok(memberAuthService.register(reqVO, ClientIpResolver.resolve(httpReq)));
    }

    /** 邮箱 + 密码登录, 返回 token + 会员信息. */
    @PostMapping("/login")
    public Result<PortalAuthLoginRespVO> login(@RequestBody @Valid PortalLoginReqVO reqVO,
                                                HttpServletRequest httpReq) {
        return Result.ok(memberAuthService.login(reqVO, ClientIpResolver.resolve(httpReq)));
    }

    /** 登出当前 token; 幂等. */
    @PostMapping("/logout")
    public Result<Void> logout() {
        memberAuthService.logout();
        return Result.ok();
    }
}
