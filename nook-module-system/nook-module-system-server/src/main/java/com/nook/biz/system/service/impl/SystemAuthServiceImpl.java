package com.nook.biz.system.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.system.api.enums.SystemUserStatusEnum;
import com.nook.biz.system.constant.SystemErrorCode;
import com.nook.biz.system.controller.auth.vo.AuthLoginReqVO;
import com.nook.biz.system.controller.auth.vo.AuthLoginRespVO;
import com.nook.biz.system.convert.user.SystemUserConvert;
import com.nook.biz.system.entity.SystemUser;
import com.nook.biz.system.mapper.SystemUserMapper;
import com.nook.biz.system.service.SystemAuthService;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.security.stp.StpSystemUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 后台认证 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class SystemAuthServiceImpl implements SystemAuthService {

    @Resource
    private SystemUserMapper systemUserMapper;
    @Resource
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    public AuthLoginRespVO login(AuthLoginReqVO reqVO, String clientIp) {
        // 按用户名查用户
        SystemUser user = systemUserMapper.selectByUsername(reqVO.getUsername());
        // 用户不存在 / 密码错误统一返回 LOGIN_FAILED, 避免账户枚举
        if (ObjectUtil.isNull(user) || !bCryptPasswordEncoder.matches(reqVO.getPassword(), user.getPasswordHash())) {
            log.warn("[login] 后台登录失败: username={}, ip={}", reqVO.getUsername(), clientIp);
            throw new BusinessException(SystemErrorCode.LOGIN_FAILED);
        }
        // 禁用账号拒绝登录
        if (SystemUserStatusEnum.DISABLED.matches(user.getStatus())) {
            throw new BusinessException(SystemErrorCode.ACCOUNT_DISABLED);
        }
        // 登录并记录登录时间与 IP
        StpSystemUtil.login(user.getId());
        systemUserMapper.updateLastLogin(user.getId(), clientIp, LocalDateTime.now());
        log.info("[login] 后台登录成功: userId={}, username={}, ip={}", user.getId(), user.getUsername(), clientIp);
        // 组装 token + 用户信息返回
        String token = StpSystemUtil.getTokenValue();
        long expiresIn = StpSystemUtil.getTokenTimeout();
        return SystemUserConvert.INSTANCE.toLoginRespVO(token, expiresIn, user);
    }

    @Override
    public void logout() {
        // 没登录也不抛错, 幂等
        if (StpSystemUtil.isLogin()) {
            StpSystemUtil.logout();
        }
    }

    @Override
    public SystemUser getLoginUser() {
        String userId = StpSystemUtil.getLoginIdAsString();
        SystemUser user = systemUserMapper.selectById(userId);
        // token 还在但用户已被删除 (deleted=1 被 MP 过滤), 视为未登录
        if (ObjectUtil.isNull(user)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return user;
    }
}
