package com.nook.biz.system.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.system.constant.SystemErrorCode;
import com.nook.biz.system.controller.user.vo.SystemUserPageReqVO;
import com.nook.biz.system.controller.user.vo.SystemUserSaveReqVO;
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
    public PageResult<SystemUser> page(SystemUserPageReqVO reqVO) {
        IPage<SystemUser> result = systemUserMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), reqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public SystemUser create(SystemUserSaveReqVO reqVO) {
        if (systemUserMapper.existsByUsername(reqVO.getUsername())) {
            throw new BusinessException(SystemErrorCode.USERNAME_EXISTS, reqVO.getUsername());
        }
        if (StrUtil.isNotBlank(reqVO.getEmail()) && systemUserMapper.existsByEmail(reqVO.getEmail())) {
            throw new BusinessException(SystemErrorCode.EMAIL_EXISTS, reqVO.getEmail());
        }
        SystemUser e = new SystemUser();
        e.setUsername(reqVO.getUsername());
        e.setPasswordHash(bCryptPasswordEncoder.encode(reqVO.getPassword()));
        e.setRealName(reqVO.getRealName());
        e.setEmail(reqVO.getEmail());
        e.setRole(reqVO.getRole());
        e.setStatus(1);
        e.setRemark(reqVO.getRemark());
        systemUserMapper.insert(e);
        return e;
    }

    @Override
    public SystemUser update(String id, SystemUserSaveReqVO reqVO) {
        SystemUser exist = systemUserMapper.selectById(id);
        if (ObjectUtil.isNull(exist)) {
            throw new BusinessException(SystemErrorCode.USER_NOT_FOUND);
        }
        // 邮箱发生改动时才查重，避免误命中自己
        if (StrUtil.isNotBlank(reqVO.getEmail())
                && !StrUtil.equals(reqVO.getEmail(), exist.getEmail())
                && systemUserMapper.existsByEmailExcludingId(reqVO.getEmail(), id)) {
            throw new BusinessException(SystemErrorCode.EMAIL_EXISTS, reqVO.getEmail());
        }
        // 编辑场景：username / password 字段在此处不生效，由前端不展示 + 校验组解耦
        exist.setRealName(reqVO.getRealName());
        exist.setEmail(reqVO.getEmail());
        if (StrUtil.isNotBlank(reqVO.getRole())) exist.setRole(reqVO.getRole());
        if (ObjectUtil.isNotNull(reqVO.getStatus())) exist.setStatus(reqVO.getStatus());
        exist.setRemark(reqVO.getRemark());
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
