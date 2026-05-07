package com.nook.biz.system.controller.admin;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.system.dto.LoginRequest;
import com.nook.biz.system.entity.SystemUser;
import com.nook.biz.system.service.SystemAuthService;
import com.nook.biz.system.service.SystemUserService;
import com.nook.biz.system.vo.LoginVO;
import com.nook.biz.system.vo.SystemUserVO;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.Result;
import com.nook.framework.security.stp.StpSystemUtil;
import com.nook.framework.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/system/auth")
@RequiredArgsConstructor
public class SystemAuthAdminController {

    private final SystemAuthService systemAuthService;
    private final SystemUserService systemUserService;

    /** 登录，返回 token + 当前用户信息。 */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginRequest req, HttpServletRequest httpReq) {
        return Result.ok(systemAuthService.login(req, ClientIpResolver.resolve(httpReq)));
    }

    /** 登出当前 token；幂等。 */
    @PostMapping("/logout")
    public Result<Void> logout() {
        systemAuthService.logout();
        return Result.ok();
    }

    /** 获取当前登录用户信息（前端刷新页面后回填）。 */
    @GetMapping("/me")
    public Result<SystemUserVO> me() {
        // SaTokenConfig 已强校验登录态，到这里 getLoginIdAsString 一定有值
        SystemUser user = systemUserService.findById(StpSystemUtil.getLoginIdAsString());
        if (ObjectUtil.isNull(user)) {
            // 极端场景：token 还在但用户已被物理删除
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return Result.ok(SystemUserVO.from(user));
    }
}
