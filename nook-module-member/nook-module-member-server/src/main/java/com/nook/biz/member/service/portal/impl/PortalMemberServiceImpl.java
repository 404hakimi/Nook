package com.nook.biz.member.service.portal.impl;

import com.nook.biz.member.constant.MemberErrorCode;
import com.nook.biz.member.controller.portal.vo.PortalChangePasswordReqVO;
import com.nook.biz.member.entity.MemberUser;
import com.nook.biz.member.mapper.MemberUserMapper;
import com.nook.biz.member.service.portal.PortalMemberService;
import com.nook.biz.member.utils.MemberSecurityUtils;
import com.nook.biz.member.validator.MemberUserValidator;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 客户端 - 会员资料 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class PortalMemberServiceImpl implements PortalMemberService {

    @Resource
    private MemberUserMapper memberUserMapper;
    @Resource
    private MemberUserValidator memberUserValidator;
    @Resource
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    public MemberUser getMemberUser(String memberId) {
        return memberUserMapper.selectById(memberId);
    }

    @Override
    public void changePassword(String memberId, PortalChangePasswordReqVO reqVO) {
        // 校验会员存在
        MemberUser member = memberUserValidator.validateExists(memberId);
        // 校验原密码
        if (!bCryptPasswordEncoder.matches(reqVO.getOldPassword(), member.getPasswordHash())) {
            log.warn("[changePassword] 原密码错误: memberId={}", memberId);
            throw new BusinessException(MemberErrorCode.OLD_PASSWORD_MISMATCH);
        }
        // 校验新密码强度
        memberUserValidator.validatePasswordStrength(reqVO.getNewPassword());
        // 加密并更新密码
        String passwordHash = bCryptPasswordEncoder.encode(reqVO.getNewPassword());
        memberUserMapper.updatePasswordHash(memberId, passwordHash);
    }

    @Override
    public String resetSubToken(String memberId) {
        // 校验会员存在
        memberUserValidator.validateExists(memberId);
        // 生成并落库新订阅 token
        String newToken = MemberSecurityUtils.generateSubToken();
        memberUserMapper.updateSubToken(memberId, newToken);
        return newToken;
    }
}
