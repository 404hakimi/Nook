package com.nook.biz.system.api.user;

import com.nook.biz.system.service.SystemUserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * 后台用户 Api 实现类
 *
 * @author nook
 */
@Service
public class SystemUserApiImpl implements SystemUserApi {

    @Resource
    private SystemUserService systemUserService;

    @Override
    public Map<String, String> getUserNameMap(Collection<String> userIds) {
        return systemUserService.getUserNameMap(userIds);
    }
}
