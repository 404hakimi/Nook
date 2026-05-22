package com.nook.biz.member.validator;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.member.constant.MemberErrorCode;
import com.nook.biz.member.entity.MemberUser;
import com.nook.biz.member.mapper.MemberUserMapper;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 会员业务校验
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class MemberUserValidator {

    private static final Pattern PASSWORD_STRONG = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[\\w!@#$%^&*()\\-+=,.?]{8,64}$");

    private final MemberUserMapper memberUserMapper;

    /** 校验会员存在; 不存在抛 MEMBER_NOT_FOUND. */
    public MemberUser validateExists(String id) {
        MemberUser e = memberUserMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND);
        }
        return e;
    }

    /** 校验邮箱唯一. */
    public void validateEmailUnique(String email) {
        if (memberUserMapper.existsByEmail(email)) {
            throw new BusinessException(MemberErrorCode.EMAIL_EXISTS, email);
        }
    }

    /** 校验密码强度: 至少 8 位且含字母 + 数字. */
    public void validatePasswordStrength(String password) {
        if (password == null || !PASSWORD_STRONG.matcher(password).matches()) {
            throw new BusinessException(MemberErrorCode.PASSWORD_TOO_WEAK);
        }
    }
}
