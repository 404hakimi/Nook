package com.nook.biz.system.mapper;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.system.dto.SystemUserQuery;
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

    /** 列表分页查询；keyword 模糊匹配 username/realName/email。 */
    default IPage<SystemUser> selectPageByQuery(IPage<SystemUser> page, SystemUserQuery query) {
        return selectPage(page, Wrappers.<SystemUser>lambdaQuery()
                .eq(ObjectUtil.isNotNull(query.getStatus()), SystemUser::getStatus, query.getStatus())
                .eq(StrUtil.isNotBlank(query.getRole()), SystemUser::getRole, query.getRole())
                .and(StrUtil.isNotBlank(query.getKeyword()), q -> q
                        .like(SystemUser::getUsername, query.getKeyword())
                        .or().like(SystemUser::getRealName, query.getKeyword())
                        .or().like(SystemUser::getEmail, query.getKeyword()))
                .orderByDesc(SystemUser::getCreatedAt));
    }
}
