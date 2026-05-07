package com.nook.biz.system.mapper;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.system.controller.user.vo.SystemUserPageReqVO;
import com.nook.biz.system.entity.SystemUser;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/** SystemUser 数据访问层；查询/更新 Wrapper 统一在此封装，Service 不直接构造 Wrapper。 */
@Mapper
public interface SystemUserMapper extends BaseMapper<SystemUser> {

    /** 按用户名精确查找；找不到返回 null。 */
    default SystemUser selectByUsername(String username) {
        return selectOne(Wrappers.<SystemUser>lambdaQuery()
                .eq(SystemUser::getUsername, username));
    }

    /** 用户名是否已存在。 */
    default boolean existsByUsername(String username) {
        return exists(Wrappers.<SystemUser>lambdaQuery()
                .eq(SystemUser::getUsername, username));
    }

    /** 邮箱是否已被使用（用于新增前查重）。 */
    default boolean existsByEmail(String email) {
        return exists(Wrappers.<SystemUser>lambdaQuery()
                .eq(SystemUser::getEmail, email));
    }

    /** 邮箱是否被除指定 id 外的用户使用（用于更新时查重）。 */
    default boolean existsByEmailExcludingId(String email, String excludeId) {
        return exists(Wrappers.<SystemUser>lambdaQuery()
                .eq(SystemUser::getEmail, email)
                .ne(SystemUser::getId, excludeId));
    }

    /** 更新登录时间与 IP。 */
    default int updateLastLogin(String id, String loginIp, LocalDateTime loginAt) {
        return update(null, Wrappers.<SystemUser>lambdaUpdate()
                .set(SystemUser::getLastLoginAt, loginAt)
                .set(SystemUser::getLastLoginIp, loginIp)
                .eq(SystemUser::getId, id));
    }

    /** 单独更新密码哈希。 */
    default int updatePasswordHash(String id, String passwordHash) {
        return update(null, Wrappers.<SystemUser>lambdaUpdate()
                .set(SystemUser::getPasswordHash, passwordHash)
                .eq(SystemUser::getId, id));
    }

    /**
     * 编辑后台用户档案。
     * realName/email/remark 一律 set(可为 null)——支持把字段"清空"。
     * role/status 只在非空时写入，避免覆盖现有值。
     */
    default int updateProfile(String id, String realName, String email,
                              String role, Integer status, String remark) {
        return update(null, Wrappers.<SystemUser>lambdaUpdate()
                .set(SystemUser::getRealName, realName)
                .set(SystemUser::getEmail, email)
                .set(StrUtil.isNotBlank(role), SystemUser::getRole, role)
                .set(ObjectUtil.isNotNull(status), SystemUser::getStatus, status)
                .set(SystemUser::getRemark, remark)
                .eq(SystemUser::getId, id));
    }

    /** 列表分页查询；keyword 模糊匹配 username/realName/email。 */
    default IPage<SystemUser> selectPageByQuery(IPage<SystemUser> page, SystemUserPageReqVO reqVO) {
        return selectPage(page, Wrappers.<SystemUser>lambdaQuery()
                .eq(ObjectUtil.isNotNull(reqVO.getStatus()), SystemUser::getStatus, reqVO.getStatus())
                .eq(StrUtil.isNotBlank(reqVO.getRole()), SystemUser::getRole, reqVO.getRole())
                .and(StrUtil.isNotBlank(reqVO.getKeyword()), q -> q
                        .like(SystemUser::getUsername, reqVO.getKeyword())
                        .or().like(SystemUser::getRealName, reqVO.getKeyword())
                        .or().like(SystemUser::getEmail, reqVO.getKeyword()))
                .orderByDesc(SystemUser::getCreatedAt));
    }
}
