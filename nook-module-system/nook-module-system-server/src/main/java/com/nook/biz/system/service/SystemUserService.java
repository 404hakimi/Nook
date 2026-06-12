package com.nook.biz.system.service;

import com.nook.biz.system.controller.user.vo.SystemUserCreateReqVO;
import com.nook.biz.system.controller.user.vo.SystemUserPageReqVO;
import com.nook.biz.system.controller.user.vo.SystemUserUpdateReqVO;
import com.nook.biz.system.entity.SystemUser;
import com.nook.common.web.response.PageResult;

import java.util.Collection;
import java.util.Map;

/**
 * 后台用户 Service 接口
 *
 * @author nook
 */
public interface SystemUserService {

    /**
     * 获得用户详情
     *
     * @param id 用户ID
     * @return 用户信息
     */
    SystemUser findById(String id);

    /**
     * 获得用户分页列表
     *
     * @param reqVO 分页条件
     * @return 分页列表
     */
    PageResult<SystemUser> page(SystemUserPageReqVO reqVO);

    /**
     * 创建用户
     *
     * @param reqVO 创建信息
     * @return 用户信息
     */
    SystemUser create(SystemUserCreateReqVO reqVO);

    /**
     * 修改用户基础信息 (用户名与密码不在此修改)
     *
     * @param id    用户ID
     * @param reqVO 更新信息
     */
    void update(String id, SystemUserUpdateReqVO reqVO);

    /**
     * 删除用户; 不能删除当前登录账号
     *
     * @param id             用户ID
     * @param currentLoginId 当前登录用户ID
     */
    void delete(String id, String currentLoginId);

    /**
     * 重置用户密码
     *
     * @param id       用户ID
     * @param password 新密码
     */
    void resetPassword(String id, String password);

    /**
     * 根据ID批量查询用户展示名 (key=用户ID, value=展示名, 真实姓名优先退回用户名; 缺失不进 map)
     *
     * @param userIds 用户ID集合
     * @return Map<String, String>
     */
    Map<String, String> getUserNameMap(Collection<String> userIds);
}
