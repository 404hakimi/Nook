package com.nook.biz.member.controller.portal;

import com.nook.biz.member.controller.portal.vo.PortalAuthLoginRespVO;
import com.nook.biz.member.controller.portal.vo.PortalLoginReqVO;
import com.nook.biz.member.controller.portal.vo.PortalRegisterReqVO;
import com.nook.biz.member.service.portal.PortalMemberAuthService;
import com.nook.common.web.response.Result;
import com.nook.framework.security.stp.StpMemberUtil;
import com.nook.framework.web.ClientIpResolver;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
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
@Validated
public class PortalAuthController {

    @Resource
    private PortalMemberAuthService portalMemberAuthService;

    /**
     * 注册新会员 (仅建账号, 不登录)
     *
     * @param reqVO 注册信息
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid PortalRegisterReqVO reqVO) {
        portalMemberAuthService.registerMember(reqVO);
        return Result.ok();
    }

    /**
     * 邮箱 + 密码登录
     *
     * @param reqVO   登录信息
     * @param httpReq HTTP 请求 (取客户端 IP)
     * @return token + 会员信息
     */
    @PostMapping("/login")
    public Result<PortalAuthLoginRespVO> login(@RequestBody @Valid PortalLoginReqVO reqVO,
                                               HttpServletRequest httpReq) {
        // 解析客户端 IP
        String clientIp = ClientIpResolver.resolve(httpReq);
        // 登录, 返回 token + 会员信息
        PortalAuthLoginRespVO respVO = portalMemberAuthService.login(reqVO, clientIp);
        return Result.ok(respVO);
    }

    /**
     * 登出当前 token; 幂等
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        if (StpMemberUtil.isLogin()) {
            StpMemberUtil.logout();
        }
        return Result.ok();
    }
}
