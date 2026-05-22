package com.nook.biz.member.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.member.constant.MemberErrorCode;
import com.nook.biz.member.controller.portal.vo.PortalAuthLoginRespVO;
import com.nook.biz.member.controller.portal.vo.PortalLoginReqVO;
import com.nook.biz.member.controller.portal.vo.PortalRegisterReqVO;
import com.nook.biz.member.convert.MemberUserConvert;
import com.nook.biz.member.entity.MemberUser;
import com.nook.biz.member.mapper.MemberUserMapper;
import com.nook.biz.member.service.MemberAuthService;
import com.nook.biz.member.validator.MemberUserValidator;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.security.stp.StpMemberUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAuthServiceImpl implements MemberAuthService {

    /** sub_token 32 char hex 碰撞重试上限; 32 char 随机空间 16^32, 实际碰撞概率极低. */
    private static final int SUB_TOKEN_RETRY = 5;

    private final MemberUserMapper memberUserMapper;
    private final MemberUserValidator memberUserValidator;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortalAuthLoginRespVO register(PortalRegisterReqVO reqVO, String clientIp) {
        memberUserValidator.validateEmailUnique(reqVO.getEmail());
        memberUserValidator.validatePasswordStrength(reqVO.getPassword());

        MemberUser entity = new MemberUser();
        entity.setEmail(reqVO.getEmail());
        entity.setPasswordHash(bCryptPasswordEncoder.encode(reqVO.getPassword()));
        entity.setSubToken(generateUniqueSubToken());
        entity.setStatus(1);
        memberUserMapper.insert(entity);
        log.info("[register] 注册成功: memberId={}, email={}, ip={}", entity.getId(), entity.getEmail(), clientIp);

        // 注册成功即自动登录, 减少前端一次请求
        StpMemberUtil.login(entity.getId());
        memberUserMapper.updateLastLogin(entity.getId(), clientIp, java.time.LocalDateTime.now());

        // 重新读一次, 拿到 MetaObjectHandler 自动填的 createdAt / 最新 lastLoginAt
        MemberUser fresh = memberUserMapper.selectById(entity.getId());
        return new PortalAuthLoginRespVO(
                StpMemberUtil.getTokenValue(),
                StpMemberUtil.getTokenTimeout(),
                MemberUserConvert.INSTANCE.convertPortal(fresh));
    }

    @Override
    public PortalAuthLoginRespVO login(PortalLoginReqVO reqVO, String clientIp) {
        MemberUser member = memberUserMapper.selectByEmail(reqVO.getEmail());
        // 用户不存在 / 密码错误统一返回 LOGIN_FAILED, 避免账户枚举
        if (ObjectUtil.isNull(member) || !bCryptPasswordEncoder.matches(reqVO.getPassword(), member.getPasswordHash())) {
            log.warn("[login] 会员登录失败: email={}, ip={}", reqVO.getEmail(), clientIp);
            throw new BusinessException(MemberErrorCode.LOGIN_FAILED);
        }
        if (ObjectUtil.equal(member.getStatus(), 2)) {
            throw new BusinessException(MemberErrorCode.ACCOUNT_DISABLED);
        }

        StpMemberUtil.login(member.getId());
        memberUserMapper.updateLastLogin(member.getId(), clientIp, java.time.LocalDateTime.now());
        log.info("[login] 会员登录成功: memberId={}, email={}, ip={}", member.getId(), member.getEmail(), clientIp);

        MemberUser fresh = memberUserMapper.selectById(member.getId());
        return new PortalAuthLoginRespVO(
                StpMemberUtil.getTokenValue(),
                StpMemberUtil.getTokenTimeout(),
                MemberUserConvert.INSTANCE.convertPortal(fresh));
    }

    @Override
    public void logout() {
        // 没登录也不抛错, 幂等
        if (StpMemberUtil.isLogin()) {
            StpMemberUtil.logout();
        }
    }

    /** 生成 32 char hex sub_token, 唯一性碰撞重试 (实际场景碰撞概率近 0). */
    private String generateUniqueSubToken() {
        for (int i = 0; i < SUB_TOKEN_RETRY; i++) {
            String candidate = IdUtil.simpleUUID();
            if (!memberUserMapper.existsBySubToken(candidate)) {
                return candidate;
            }
        }
        // 5 次都碰撞 = 出大事了 (16^32 空间), 直接抛错让上游决定
        throw new BusinessException(MemberErrorCode.SUB_TOKEN_GENERATE_FAILED);
    }
}
