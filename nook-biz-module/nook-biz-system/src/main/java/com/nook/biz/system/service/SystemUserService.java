package com.nook.biz.system.service;

import com.nook.biz.system.controller.user.vo.SystemUserPageReqVO;
import com.nook.biz.system.controller.user.vo.SystemUserSaveReqVO;
import com.nook.biz.system.entity.SystemUser;
import com.nook.common.web.response.PageResult;

/** 后台用户基础读写 + CRUD。 */
public interface SystemUserService {

    /** 按用户名查找；找不到返回 null。 */
    SystemUser findByUsername(String username);

    /** 按 ID 查找；找不到返回 null。 */
    SystemUser findById(String id);

    /** 更新最后登录时间和 IP（登录成功时由 AuthService 调用）。 */
    void updateLastLogin(String id, String loginIp);

    /** 分页查询。 */
    PageResult<SystemUser> page(SystemUserPageReqVO reqVO);

    /** 新增；用户名重复抛 USERNAME_EXISTS，邮箱重复抛 EMAIL_EXISTS。 */
    SystemUser create(SystemUserSaveReqVO reqVO);

    /** 编辑基础信息；username/password 不在此处改 (密码走 resetPassword)。 */
    SystemUser update(String id, SystemUserSaveReqVO reqVO);

    /** 逻辑删除；不能删除当前登录账号。 */
    void delete(String id, String currentLoginId);

    /** 重置密码；BCrypt 加密后写库。 */
    void resetPassword(String id, String newPlainPassword);
}
