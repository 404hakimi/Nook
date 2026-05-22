package com.nook.biz.system.api.user;

import com.nook.biz.system.service.SystemUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * 后台用户 Api 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class SystemUserApiImpl implements SystemUserApi {

    private final SystemUserService systemUserService;

    @Override
    public Map<String, String> getUserNameMap(Collection<String> userIds) {
        return systemUserService.loadUserNameMap(userIds);
    }
}
