package com.nook.biz.system.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.system.constant.SystemErrorCode;
import com.nook.biz.system.dto.CreateSystemUserDTO;
import com.nook.biz.system.dto.SystemUserQuery;
import com.nook.biz.system.dto.UpdateSystemUserDTO;
import com.nook.biz.system.entity.SystemUser;
import com.nook.biz.system.mapper.SystemUserMapper;
import com.nook.biz.system.service.SystemUserService;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SystemUserServiceImpl implements SystemUserService {

    private final SystemUserMapper systemUserMapper;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    public SystemUser findByUsername(String username) {
        return systemUserMapper.selectByUsername(username);
    }

    @Override
    public SystemUser findById(String id) {
        return systemUserMapper.selectById(id);
    }

    @Override
    public void updateLastLogin(String id, String loginIp) {
        systemUserMapper.updateLastLogin(id, loginIp, LocalDateTime.now());
    }

    @Override
    public PageResult<SystemUser> page(SystemUserQuery query) {
        IPage<SystemUser> result = systemUserMapper.selectPageByQuery(
                Page.of(query.getPage(), query.getSize()), query);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public SystemUser create(CreateSystemUserDTO dto) {
        if (systemUserMapper.existsByUsername(dto.getUsername())) {
            throw new BusinessException(SystemErrorCode.USERNAME_EXISTS, dto.getUsername());
        }
        if (StrUtil.isNotBlank(dto.getEmail()) && systemUserMapper.existsByEmail(dto.getEmail())) {
            throw new BusinessException(SystemErrorCode.EMAIL_EXISTS, dto.getEmail());
        }
        SystemUser e = new SystemUser();
        e.setUsername(dto.getUsername());
        e.setPasswordHash(bCryptPasswordEncoder.encode(dto.getPassword()));
        e.setRealName(dto.getRealName());
        e.setEmail(dto.getEmail());
        e.setRole(dto.getRole());
        e.setStatus(1);
        e.setRemark(dto.getRemark());
        systemUserMapper.insert(e);
        return e;
    }

    @Override
    public SystemUser update(String id, UpdateSystemUserDTO dto) {
        SystemUser exist = systemUserMapper.selectById(id);
        if (ObjectUtil.isNull(exist)) {
            throw new BusinessException(SystemErrorCode.USER_NOT_FOUND);
        }
        // 邮箱发生改动时才查重，避免误命中自己
        if (StrUtil.isNotBlank(dto.getEmail())
                && !StrUtil.equals(dto.getEmail(), exist.getEmail())
                && systemUserMapper.existsByEmailExcludingId(dto.getEmail(), id)) {
            throw new BusinessException(SystemErrorCode.EMAIL_EXISTS, dto.getEmail());
        }
        exist.setRealName(dto.getRealName());
        exist.setEmail(dto.getEmail());
        if (StrUtil.isNotBlank(dto.getRole())) exist.setRole(dto.getRole());
        if (ObjectUtil.isNotNull(dto.getStatus())) exist.setStatus(dto.getStatus());
        exist.setRemark(dto.getRemark());
        systemUserMapper.updateById(exist);
        return exist;
    }

    @Override
    public void delete(String id, String currentLoginId) {
        if (StrUtil.equals(id, currentLoginId)) {
            throw new BusinessException(SystemErrorCode.CANNOT_DELETE_SELF);
        }
        SystemUser exist = systemUserMapper.selectById(id);
        if (ObjectUtil.isNull(exist)) {
            throw new BusinessException(SystemErrorCode.USER_NOT_FOUND);
        }
        // BaseEntity 上的 @TableLogic 让 deleteById 自动转 UPDATE 设 deleted=1
        systemUserMapper.deleteById(id);
    }

    @Override
    public void resetPassword(String id, String newPlainPassword) {
        SystemUser exist = systemUserMapper.selectById(id);
        if (ObjectUtil.isNull(exist)) {
            throw new BusinessException(SystemErrorCode.USER_NOT_FOUND);
        }
        systemUserMapper.updatePasswordHash(id, bCryptPasswordEncoder.encode(newPlainPassword));
    }
}
