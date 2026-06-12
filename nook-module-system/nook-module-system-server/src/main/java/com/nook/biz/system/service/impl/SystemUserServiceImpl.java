package com.nook.biz.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.system.api.enums.SystemUserStatusEnum;
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
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 后台用户 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class SystemUserServiceImpl implements SystemUserService {

    @Resource
    private SystemUserMapper systemUserMapper;
    @Resource
    private SystemUserValidator systemUserValidator;
    @Resource
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    public SystemUser findById(String id) {
        return systemUserValidator.validateExists(id);
    }

    @Override
    public PageResult<SystemUser> page(SystemUserPageReqVO reqVO) {
        IPage<SystemUser> result = systemUserMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), reqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public SystemUser create(SystemUserCreateReqVO reqVO) {
        // 校验用户名 / 邮箱唯一 + 角色 / 状态取值 + 密码强度
        systemUserValidator.validateUsernameUnique(reqVO.getUsername());
        systemUserValidator.validateEmailUnique(null, reqVO.getEmail());
        systemUserValidator.validateRole(reqVO.getRole());
        systemUserValidator.validateStatus(reqVO.getStatus());
        systemUserValidator.validatePasswordStrength(reqVO.getPassword());
        // 加密密码
        String passwordHash = bCryptPasswordEncoder.encode(reqVO.getPassword());
        // 插入用户; 状态不传默认正常
        SystemUser entity = BeanUtils.toBean(reqVO, SystemUser.class);
        entity.setPasswordHash(passwordHash);
        if (ObjectUtil.isNull(entity.getStatus())) {
            entity.setStatus(SystemUserStatusEnum.NORMAL.getCode());
        }
        systemUserMapper.insert(entity);
        log.info("[create] 创建后台用户: userId={}, username={}", entity.getId(), entity.getUsername());
        return entity;
    }

    @Override
    public void update(String id, SystemUserUpdateReqVO reqVO) {
        // 校验存在 + 邮箱唯一 (排除自身) + 角色 / 状态取值
        systemUserValidator.validateExists(id);
        systemUserValidator.validateEmailUnique(id, reqVO.getEmail());
        systemUserValidator.validateRole(reqVO.getRole());
        systemUserValidator.validateStatus(reqVO.getStatus());
        // 更新基础信息; null 字段由 MP NOT_NULL 策略跳过, 即"保留原值"
        SystemUser entity = BeanUtils.toBean(reqVO, SystemUser.class);
        entity.setId(id);
        systemUserMapper.updateById(entity);
    }

    @Override
    public void delete(String id, String currentLoginId) {
        // 校验非自身 + 用户存在
        systemUserValidator.validateNotSelf(id, currentLoginId);
        systemUserValidator.validateExists(id);
        // 逻辑删除
        systemUserMapper.deleteById(id);
        log.info("[delete] 删除后台用户: userId={}, operatorId={}", id, currentLoginId);
    }

    @Override
    public void resetPassword(String id, String password) {
        // 校验存在 + 新密码强度
        systemUserValidator.validateExists(id);
        systemUserValidator.validatePasswordStrength(password);
        // 加密并更新密码
        String passwordHash = bCryptPasswordEncoder.encode(password);
        systemUserMapper.updatePasswordHash(id, passwordHash);
        log.info("[resetPassword] 重置后台用户密码: userId={}", id);
    }

    @Override
    public Map<String, String> getUserNameMap(Collection<String> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        // 批量查用户
        List<SystemUser> users = systemUserMapper.selectBatchIds(userIds);
        // 提取用户ID → 展示名 (真实姓名优先, 退回用户名)
        return CollectionUtils.convertMap(users, SystemUser::getId,
                u -> StrUtil.blankToDefault(u.getRealName(), u.getUsername()));
    }
}
