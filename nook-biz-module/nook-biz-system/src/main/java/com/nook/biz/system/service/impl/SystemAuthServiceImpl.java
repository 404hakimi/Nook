package com.nook.biz.system.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.system.constant.SystemErrorCode;
import com.nook.biz.system.dto.LoginRequest;
import com.nook.biz.system.entity.SystemUser;
import com.nook.biz.system.service.SystemAuthService;
import com.nook.biz.system.service.SystemUserService;
import com.nook.biz.system.vo.LoginVO;
import com.nook.biz.system.vo.SystemUserVO;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.security.stp.StpSystemUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemAuthServiceImpl implements SystemAuthService {

    private final SystemUserService systemUserService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    public LoginVO login(LoginRequest req, String clientIp) {
        SystemUser user = systemUserService.findByUsername(req.getUsername());
        // 用户不存在 / 密码错误统一返回 LOGIN_FAILED，避免账户枚举
        if (ObjectUtil.isNull(user) || !bCryptPasswordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            log.warn("[后台登录失败] username={} ip={}", req.getUsername(), clientIp);
            throw new BusinessException(SystemErrorCode.LOGIN_FAILED);
        }
        if (ObjectUtil.equal(user.getStatus(), 2)) {
            throw new BusinessException(SystemErrorCode.ACCOUNT_DISABLED);
        }

        // sa-token 自动生成 token、写 Redis、设置 TTL
        StpSystemUtil.login(user.getId());
        systemUserService.updateLastLogin(user.getId(), clientIp);
        log.info("[后台登录成功] userId={} username={} ip={}", user.getId(), user.getUsername(), clientIp);

        return new LoginVO(StpSystemUtil.getTokenValue(), StpSystemUtil.getTokenTimeout(), SystemUserVO.from(user));
    }

    @Override
    public void logout() {
        // 没登录也不抛错，幂等
        if (StpSystemUtil.isLogin()) {
            StpSystemUtil.logout();
        }
    }
}
