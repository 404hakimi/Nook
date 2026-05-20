package com.nook.biz.member.service.impl;

import cn.hutool.core.util.IdUtil;
import com.nook.biz.member.constant.MemberErrorCode;
import com.nook.biz.member.controller.portal.vo.PortalChangePasswordReqVO;
import com.nook.biz.member.controller.portal.vo.PortalMemberRespVO;
import com.nook.biz.member.convert.MemberUserConvert;
import com.nook.biz.member.entity.MemberUser;
import com.nook.biz.member.mapper.MemberUserMapper;
import com.nook.biz.member.service.MemberProfileService;
import com.nook.biz.member.validator.MemberUserValidator;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberProfileServiceImpl implements MemberProfileService {

    private static final int SUB_TOKEN_RETRY = 5;

    private final MemberUserMapper memberUserMapper;
    private final MemberUserValidator memberUserValidator;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    public PortalMemberRespVO getProfile(String memberId) {
        MemberUser member = memberUserValidator.validateExists(memberId);
        return MemberUserConvert.INSTANCE.convertPortal(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(String memberId, PortalChangePasswordReqVO reqVO) {
        MemberUser member = memberUserValidator.validateExists(memberId);
        if (!bCryptPasswordEncoder.matches(reqVO.getOldPassword(), member.getPasswordHash())) {
            log.warn("[changePassword] 原密码错误: memberId={}", memberId);
            throw new BusinessException(MemberErrorCode.OLD_PASSWORD_MISMATCH);
        }
        memberUserValidator.validatePasswordStrength(reqVO.getNewPassword());

        memberUserMapper.updatePasswordHash(memberId, bCryptPasswordEncoder.encode(reqVO.getNewPassword()));
        log.info("[changePassword] 密码已修改: memberId={}", memberId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetSubToken(String memberId) {
        memberUserValidator.validateExists(memberId);
        String newToken = generateUniqueSubToken();
        memberUserMapper.updateSubToken(memberId, newToken);
        log.info("[resetSubToken] sub_token 已重置: memberId={}", memberId);
        return newToken;
    }

    private String generateUniqueSubToken() {
        for (int i = 0; i < SUB_TOKEN_RETRY; i++) {
            String candidate = IdUtil.simpleUUID();
            if (!memberUserMapper.existsBySubToken(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(MemberErrorCode.SUB_TOKEN_GENERATE_FAILED);
    }
}
