package com.nook.biz.system.service;

import com.nook.biz.system.entity.SystemUser;

/** 后台用户基础读写。 */
public interface SystemUserService {

    /** 按用户名查找；找不到返回 null（不抛异常以便鉴权层做差异化处理）。 */
    SystemUser findByUsername(String username);

    /** 按 ID 查找；找不到返回 null。 */
    SystemUser findById(String id);

    /** 更新最后登录时间和 IP（登录成功时由 AuthService 调用）。 */
    void updateLastLogin(String id, String loginIp);
}
