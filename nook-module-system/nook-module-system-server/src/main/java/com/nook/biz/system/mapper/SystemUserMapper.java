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

/**
 * 后台用户 Mapper
 *
 * @author nook
 */
@Mapper
public interface SystemUserMapper extends BaseMapper<SystemUser> {

    default SystemUser selectByUsername(String username) {
        return selectOne(Wrappers.<SystemUser>lambdaQuery()
                .eq(SystemUser::getUsername, username));
    }

    default boolean existsByUsername(String username) {
        return exists(Wrappers.<SystemUser>lambdaQuery()
                .eq(SystemUser::getUsername, username));
    }

    default boolean existsByEmail(String email) {
        return exists(Wrappers.<SystemUser>lambdaQuery()
                .eq(SystemUser::getEmail, email));
    }

    default boolean existsByEmailExcludingId(String email, String excludeId) {
        return exists(Wrappers.<SystemUser>lambdaQuery()
                .eq(SystemUser::getEmail, email)
                .ne(SystemUser::getId, excludeId));
    }

    default int updateLastLogin(String id, String loginIp, LocalDateTime loginAt) {
        return update(null, Wrappers.<SystemUser>lambdaUpdate()
                .set(SystemUser::getLastLoginAt, loginAt)
                .set(SystemUser::getLastLoginIp, loginIp)
                .set(SystemUser::getUpdatedAt, LocalDateTime.now())
                .eq(SystemUser::getId, id));
    }

    default int updatePasswordHash(String id, String passwordHash) {
        return update(null, Wrappers.<SystemUser>lambdaUpdate()
                .set(SystemUser::getPasswordHash, passwordHash)
                .set(SystemUser::getUpdatedAt, LocalDateTime.now())
                .eq(SystemUser::getId, id));
    }

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
