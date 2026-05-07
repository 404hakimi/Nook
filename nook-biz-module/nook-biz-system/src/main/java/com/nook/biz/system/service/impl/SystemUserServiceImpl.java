package com.nook.biz.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.system.entity.SystemUser;
import com.nook.biz.system.mapper.SystemUserMapper;
import com.nook.biz.system.service.SystemUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SystemUserServiceImpl implements SystemUserService {

    private final SystemUserMapper mapper;

    @Override
    public SystemUser findByUsername(String username) {
        return mapper.selectOne(
                Wrappers.<SystemUser>lambdaQuery()
                        .eq(SystemUser::getUsername, username));
    }

    @Override
    public SystemUser findById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public void updateLastLogin(String id, String loginIp) {
        mapper.update(null,
                Wrappers.<SystemUser>lambdaUpdate()
                        .set(SystemUser::getLastLoginAt, LocalDateTime.now())
                        .set(SystemUser::getLastLoginIp, loginIp)
                        .eq(SystemUser::getId, id));
    }
}
