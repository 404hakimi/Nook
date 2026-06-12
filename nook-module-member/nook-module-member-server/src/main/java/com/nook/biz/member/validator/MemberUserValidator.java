package com.nook.biz.member.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.member.constant.MemberErrorCode;
import com.nook.biz.member.entity.MemberUser;
import com.nook.biz.member.mapper.MemberUserMapper;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 会员业务校验
 *
 * @author nook
 */
@Component
public class MemberUserValidator {

    private static final Pattern PASSWORD_STRONG = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[\\w!@#$%^&*()\\-+=,.?]{8,64}$");

    @Resource
    private MemberUserMapper memberUserMapper;

    /**
     * 校验会员存在
     *
     * @param id 会员ID
     * @return 会员信息
     */
    public MemberUser validateExists(String id) {
        MemberUser e = memberUserMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND);
        }
        return e;
    }

    /**
     * 校验邮箱唯一
     *
     * @param email 邮箱
     */
    public void validateEmailUnique(String email) {
        if (memberUserMapper.existsByEmail(email)) {
            throw new BusinessException(MemberErrorCode.EMAIL_EXISTS, email);
        }
    }

    /**
     * 校验密码强度: 至少 8 位且含字母 + 数字
     *
     * @param password 明文密码
     */
    public void validatePasswordStrength(String password) {
        if (StrUtil.isBlank(password) || !PASSWORD_STRONG.matcher(password).matches()) {
            throw new BusinessException(MemberErrorCode.PASSWORD_TOO_WEAK);
        }
    }
}
