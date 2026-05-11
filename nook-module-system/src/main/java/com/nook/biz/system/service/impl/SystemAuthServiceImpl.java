package com.nook.biz.system.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.system.constant.SystemErrorCode;
import com.nook.biz.system.controller.auth.vo.AuthLoginReqVO;
import com.nook.biz.system.controller.auth.vo.AuthLoginRespVO;
import com.nook.biz.system.convert.SystemUserConvert;
import com.nook.biz.system.entity.SystemUser;
import com.nook.biz.system.service.SystemAuthService;
import com.nook.biz.system.service.SystemUserService;
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
    public AuthLoginRespVO login(AuthLoginReqVO reqVO, String clientIp) {
        SystemUser user = systemUserService.findByUsername(reqVO.getUsername());
        // 用户不存在 / 密码错误统一返回 LOGIN_FAILED，避免账户枚举
        if (ObjectUtil.isNull(user) || !bCryptPasswordEncoder.matches(reqVO.getPassword(), user.getPasswordHash())) {
            log.warn("[后台登录失败] username={} ip={}", reqVO.getUsername(), clientIp);
            throw new BusinessException(SystemErrorCode.LOGIN_FAILED);
        }
        if (ObjectUtil.equal(user.getStatus(), 2)) {
            throw new BusinessException(SystemErrorCode.ACCOUNT_DISABLED);
        }

        // sa-token 自动生成 token、写 Redis、设置 TTL
        StpSystemUtil.login(user.getId());
        systemUserService.updateLastLogin(user.getId(), clientIp);
        log.info("[后台登录成功] userId={} username={} ip={}", user.getId(), user.getUsername(), clientIp);

        return new AuthLoginRespVO(
                StpSystemUtil.getTokenValue(),
                StpSystemUtil.getTokenTimeout(),
                SystemUserConvert.INSTANCE.convert(user));
    }

    @Override
    public void logout() {
        // 没登录也不抛错，幂等
        if (StpSystemUtil.isLogin()) {
            StpSystemUtil.logout();
        }
    }
}
