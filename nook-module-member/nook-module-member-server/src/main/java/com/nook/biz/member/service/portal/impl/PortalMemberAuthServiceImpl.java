package com.nook.biz.member.service.portal.impl;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.member.api.enums.MemberUserStatusEnum;
import com.nook.biz.member.constant.MemberErrorCode;
import com.nook.biz.member.controller.portal.vo.PortalAuthLoginRespVO;
import com.nook.biz.member.controller.portal.vo.PortalLoginReqVO;
import com.nook.biz.member.controller.portal.vo.PortalRegisterReqVO;
import com.nook.biz.member.convert.portal.PortalMemberUserConvert;
import com.nook.biz.member.entity.MemberUser;
import com.nook.biz.member.mapper.MemberUserMapper;
import com.nook.biz.member.service.portal.PortalMemberAuthService;
import com.nook.biz.member.utils.MemberSecurityUtils;
import com.nook.biz.member.validator.MemberUserValidator;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.security.stp.StpMemberUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 客户端 - 会员认证 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class PortalMemberAuthServiceImpl implements PortalMemberAuthService {

    @Resource
    private MemberUserMapper memberUserMapper;
    @Resource
    private MemberUserValidator memberUserValidator;
    @Resource
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    public void registerMember(PortalRegisterReqVO reqVO) {
        // 校验邮箱唯一 (唯一索引兜底)
        memberUserValidator.validateEmailUnique(reqVO.getEmail());
        // 校验密码强度
        memberUserValidator.validatePasswordStrength(reqVO.getPassword());
        // 加密密码, 生成订阅 token
        String passwordHash = bCryptPasswordEncoder.encode(reqVO.getPassword());
        String subToken = MemberSecurityUtils.generateSubToken();
        // 插入会员信息
        MemberUser entity = new MemberUser();
        entity.setEmail(reqVO.getEmail());
        entity.setPasswordHash(passwordHash);
        entity.setSubToken(subToken);
        entity.setStatus(MemberUserStatusEnum.NORMAL.getCode());
        memberUserMapper.insert(entity);
    }

    @Override
    public PortalAuthLoginRespVO login(PortalLoginReqVO reqVO, String clientIp) {
        // 按邮箱查会员
        MemberUser member = memberUserMapper.selectByEmail(reqVO.getEmail());
        // 用户不存在 / 密码错误统一返回 LOGIN_FAILED, 避免账户枚举
        if (ObjectUtil.isNull(member) || !bCryptPasswordEncoder.matches(reqVO.getPassword(), member.getPasswordHash())) {
            log.warn("[login] 会员登录失败: email={}, ip={}", reqVO.getEmail(), clientIp);
            throw new BusinessException(MemberErrorCode.LOGIN_FAILED);
        }
        // 禁用会员拒绝登录
        if (MemberUserStatusEnum.DISABLED.matches(member.getStatus())) {
            throw new BusinessException(MemberErrorCode.ACCOUNT_DISABLED);
        }
        // 登录并记录登录时间与 IP
        StpMemberUtil.login(member.getId());
        memberUserMapper.updateLastLogin(member.getId(), clientIp, LocalDateTime.now());
        log.info("[login] 会员登录成功: memberId={}, email={}, ip={}", member.getId(), member.getEmail(), clientIp);
        // 重查拿到含最新登录时间的会员信息, 组装 token + 会员信息返回
        MemberUser fresh = memberUserMapper.selectById(member.getId());
        String token = StpMemberUtil.getTokenValue();
        long expiresIn = StpMemberUtil.getTokenTimeout();
        return PortalMemberUserConvert.INSTANCE.toLoginRespVO(token, expiresIn, fresh);
    }
}
