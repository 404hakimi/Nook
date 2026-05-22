package com.nook.biz.system.service;

import com.nook.biz.system.controller.user.vo.SystemUserCreateReqVO;
import com.nook.biz.system.controller.user.vo.SystemUserPageReqVO;
import com.nook.biz.system.controller.user.vo.SystemUserUpdateReqVO;
import com.nook.biz.system.entity.SystemUser;
import com.nook.common.web.response.PageResult;

import java.util.Collection;
import java.util.Map;

/**
 * 后台用户基础读写 + CRUD.
 *
 * @author nook
 */
public interface SystemUserService {

    /**
     * 按用户名精确查找; 找不到返回 null (供 AuthService 登录态判定).
     *
     * @param username 用户名
     * @return SystemUser
     */
    SystemUser findByUsername(String username);

    /**
     * 按 ID 查找; 找不到抛 USER_NOT_FOUND (与 list/page 列表读不同, 这里语义上必须存在).
     *
     * @param id system_user.id
     * @return SystemUser
     */
    SystemUser findById(String id);

    /**
     * 更新最后登录时间和 IP, 登录成功时由 AuthService 调用.
     *
     * @param id      system_user.id
     * @param loginIp 登录 IP
     */
    void updateLastLogin(String id, String loginIp);

    /**
     * 分页查询用户.
     *
     * @param reqVO 分页 + 过滤条件
     * @return PageResult of SystemUser
     */
    PageResult<SystemUser> page(SystemUserPageReqVO reqVO);

    /**
     * 新增用户; BCrypt 加密密码后落库; 用户名/邮箱重复抛 USERNAME_EXISTS / EMAIL_EXISTS.
     *
     * @param reqVO 新增入参
     * @return SystemUser
     */
    SystemUser create(SystemUserCreateReqVO reqVO);

    /**
     * 编辑用户基础信息; username/password 不在此修改 (密码走 resetPassword); null 字段保留原值.
     *
     * @param id    system_user.id
     * @param reqVO 编辑入参
     */
    void update(String id, SystemUserUpdateReqVO reqVO);

    /**
     * 逻辑删除用户; 不能删除当前登录账号.
     *
     * @param id             system_user.id
     * @param currentLoginId 当前登录 id
     */
    void delete(String id, String currentLoginId);

    /**
     * 重置密码; BCrypt 加密后写库; 不需要旧密码 (超管行为).
     *
     * @param id               system_user.id
     * @param newPlainPassword 新密码明文
     */
    void resetPassword(String id, String newPlainPassword);

    /**
     * 批量取 friendly name (realName 优先, 退回 username); 走 selectBatchIds 避免 N+1; 缺失 id 不进结果 map.
     *
     * @param userIds id 集合; null / 空返空 map
     * @return Map of userId → name
     */
    Map<String, String> loadUserNameMap(Collection<String> userIds);
}
