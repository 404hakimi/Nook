package com.nook.biz.system.api.user;

import com.nook.biz.system.service.SystemUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/** system-api {@link SystemUserApi} 实现: 委托 SystemUserService. */
@Service
@RequiredArgsConstructor
public class SystemUserApiImpl implements SystemUserApi {

    private final SystemUserService systemUserService;

    @Override
    public Map<String, String> getUserNameMap(Collection<String> userIds) {
        return systemUserService.loadUserNameMap(userIds);
    }
}
