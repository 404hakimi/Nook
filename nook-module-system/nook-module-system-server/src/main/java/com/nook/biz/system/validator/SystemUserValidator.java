package com.nook.biz.system.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.system.api.enums.SystemUserRoleEnum;
import com.nook.biz.system.api.enums.SystemUserStatusEnum;
import com.nook.biz.system.constant.SystemErrorCode;
import com.nook.biz.system.entity.SystemUser;
import com.nook.biz.system.mapper.SystemUserMapper;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 后台用户业务校验
 *
 * @author nook
 */
@Component
public class SystemUserValidator {

    private static final Pattern PASSWORD_STRONG = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[\\w!@#$%^&*()\\-+=,.?]{8,64}$");

    @Resource
    private SystemUserMapper systemUserMapper;

    /**
     * 校验用户存在
     *
     * @param id 主键ID
     * @return 用户信息
     */
    public SystemUser validateExists(String id) {
        SystemUser e = systemUserMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(SystemErrorCode.USER_NOT_FOUND);
        }
        return e;
    }

    /**
     * 校验用户名唯一
     *
     * @param username 用户名
     */
    public void validateUsernameUnique(String username) {
        if (systemUserMapper.existsByUsername(username)) {
            throw new BusinessException(SystemErrorCode.USERNAME_EXISTS, username);
        }
    }

    /**
     * 校验邮箱唯一; id 非空时排除自身 (Update 不冲突自己); 邮箱为空跳过
     *
     * @param id    当前行 id, Create 传 null
     * @param email 邮箱
     */
    public void validateEmailUnique(String id, String email) {
        if (StrUtil.isBlank(email)) {
            return;
        }
        boolean dup = id == null
                ? systemUserMapper.existsByEmail(email)
                : systemUserMapper.existsByEmailExcludingId(email, id);
        if (dup) {
            throw new BusinessException(SystemErrorCode.EMAIL_EXISTS, email);
        }
    }

    /**
     * 校验目标非当前登录账号
     *
     * @param id             目标用户ID
     * @param currentLoginId 当前登录用户ID
     */
    public void validateNotSelf(String id, String currentLoginId) {
        if (StrUtil.equals(id, currentLoginId)) {
            throw new BusinessException(SystemErrorCode.CANNOT_DELETE_SELF);
        }
    }

    /**
     * 校验角色取值合法; 为空跳过
     *
     * @param role 角色
     */
    public void validateRole(String role) {
        if (StrUtil.isBlank(role)) {
            return;
        }
        if (SystemUserRoleEnum.fromCode(role) == null) {
            throw new BusinessException(SystemErrorCode.INVALID_ROLE, role);
        }
    }

    /**
     * 校验状态取值合法; 为空跳过
     *
     * @param status 状态
     */
    public void validateStatus(Integer status) {
        if (ObjectUtil.isNull(status)) {
            return;
        }
        if (SystemUserStatusEnum.fromCode(status) == null) {
            throw new BusinessException(SystemErrorCode.INVALID_STATUS, status);
        }
    }

    /**
     * 校验密码强度: 至少 8 位且含字母 + 数字
     *
     * @param password 明文密码
     */
    public void validatePasswordStrength(String password) {
        if (StrUtil.isBlank(password) || !PASSWORD_STRONG.matcher(password).matches()) {
            throw new BusinessException(SystemErrorCode.PASSWORD_TOO_WEAK);
        }
    }
}
