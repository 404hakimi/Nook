package com.nook.biz.system.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.system.controller.user.vo.SystemUserCreateReqVO;
import com.nook.biz.system.controller.user.vo.SystemUserPageReqVO;
import com.nook.biz.system.controller.user.vo.SystemUserUpdateReqVO;
import com.nook.biz.system.entity.SystemUser;
import com.nook.biz.system.mapper.SystemUserMapper;
import com.nook.biz.system.service.SystemUserService;
import com.nook.biz.system.validator.SystemUserValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemUserServiceImpl implements SystemUserService {

    private final SystemUserMapper systemUserMapper;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final SystemUserValidator systemUserValidator;

    @Override
    public SystemUser findByUsername(String username) {
        return systemUserMapper.selectByUsername(username);
    }

    @Override
    public SystemUser findById(String id) {
        return systemUserValidator.validateExists(id);
    }

    @Override
    public void updateLastLogin(String id, String loginIp) {
        systemUserMapper.updateLastLogin(id, loginIp, LocalDateTime.now());
    }

    @Override
    public PageResult<SystemUser> page(SystemUserPageReqVO reqVO) {
        IPage<SystemUser> result = systemUserMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), reqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public SystemUser create(SystemUserCreateReqVO reqVO) {
        // 校验用户名唯一
        systemUserValidator.validateUsernameUnique(reqVO.getUsername());
        // 校验邮箱唯一
        systemUserValidator.validateEmailUnique(null, reqVO.getEmail());

        // 插入用户; 密码 BCrypt 加密, status 默认 1=正常
        SystemUser entity = BeanUtils.toBean(reqVO, SystemUser.class);
        entity.setPasswordHash(bCryptPasswordEncoder.encode(reqVO.getPassword()));
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        systemUserMapper.insert(entity);
        return entity;
    }

    @Override
    public void update(String id, SystemUserUpdateReqVO reqVO) {
        // 校验用户存在
        systemUserValidator.validateExists(id);
        // 校验邮箱唯一 (排除自身)
        systemUserValidator.validateEmailUnique(id, reqVO.getEmail());

        // 更新用户基础信息; null 字段由 MP NOT_NULL 策略跳过, 即"保留原值"
        SystemUser entity = BeanUtils.toBean(reqVO, SystemUser.class);
        systemUserMapper.update(entity, Wrappers.<SystemUser>lambdaUpdate().eq(SystemUser::getId, id));
    }

    @Override
    public void delete(String id, String currentLoginId) {
        // 校验非自身
        systemUserValidator.validateNotSelf(id, currentLoginId);
        // 校验用户存在
        systemUserValidator.validateExists(id);
        // 逻辑删除用户
        systemUserMapper.deleteById(id);
    }

    @Override
    public void resetPassword(String id, String newPlainPassword) {
        // 校验用户存在
        systemUserValidator.validateExists(id);
        // 更新密码哈希
        systemUserMapper.updatePasswordHash(id, bCryptPasswordEncoder.encode(newPlainPassword));
    }

    @Override
    public Map<String, String> loadUserNameMap(Collection<String> userIds) {
        if (CollectionUtils.isAnyEmpty(userIds)) return Collections.emptyMap();
        return CollectionUtils.convertMap(systemUserMapper.selectBatchIds(userIds), SystemUser::getId,
                // realName 优先, 缺失退回 username (登录账号)
                u -> u.getRealName() != null && !u.getRealName().isEmpty() ? u.getRealName() : u.getUsername());
    }
}
