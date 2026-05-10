package com.nook.biz.system.validator;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.system.constant.SystemErrorCode;
import com.nook.biz.system.entity.SystemUser;
import com.nook.biz.system.mapper.SystemUserMapper;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 后台用户业务校验.
 *
 * @author nook
 */
@Component
public class SystemUserValidator {

    @Resource
    private SystemUserMapper systemUserMapper;

    /**
     * 校验用户存在.
     *
     * @param id system_user.id
     * @return SystemUser
     */
    public SystemUser validateExists(String id) {
        SystemUser e = systemUserMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(SystemErrorCode.USER_NOT_FOUND);
        }
        return e;
    }

    /**
     * 校验用户名唯一; 仅在 Create 路径调用 (Update 不允许修改 username).
     *
     * @param username 用户名
     */
    public void validateUsernameUnique(String username) {
        if (systemUserMapper.existsByUsername(username)) {
            throw new BusinessException(SystemErrorCode.USERNAME_EXISTS, username);
        }
    }

    /**
     * 校验邮箱唯一; id 传当前行 id 用于排除自身 (Update), Create 传 null; 邮箱为空跳过.
     *
     * @param id    当前行 id (Create 传 null)
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
     * 校验非自身; 用于禁止删除/禁用当前登录账号.
     *
     * @param id             目标 id
     * @param currentLoginId 当前登录 id
     */
    public void validateNotSelf(String id, String currentLoginId) {
        if (StrUtil.equals(id, currentLoginId)) {
            throw new BusinessException(SystemErrorCode.CANNOT_DELETE_SELF);
        }
    }
}
