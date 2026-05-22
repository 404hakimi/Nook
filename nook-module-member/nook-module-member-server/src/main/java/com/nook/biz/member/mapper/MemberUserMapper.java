package com.nook.biz.member.mapper;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.member.controller.admin.vo.AdminMemberPageReqVO;
import com.nook.biz.member.entity.MemberUser;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * 会员 Mapper
 *
 * @author nook
 */
@Mapper
public interface MemberUserMapper extends BaseMapper<MemberUser> {

    /** 按邮箱精确查找; 找不到返回 null. */
    default MemberUser selectByEmail(String email) {
        return selectOne(Wrappers.<MemberUser>lambdaQuery()
                .eq(MemberUser::getEmail, email));
    }

    /** 按 sub_token 精确查找; 找不到返回 null. */
    default MemberUser selectBySubToken(String subToken) {
        return selectOne(Wrappers.<MemberUser>lambdaQuery()
                .eq(MemberUser::getSubToken, subToken));
    }

    /** 邮箱是否已被使用 (注册时查重). */
    default boolean existsByEmail(String email) {
        return exists(Wrappers.<MemberUser>lambdaQuery()
                .eq(MemberUser::getEmail, email));
    }

    /** sub_token 是否已被使用 (生成时极端碰撞重试用). */
    default boolean existsBySubToken(String subToken) {
        return exists(Wrappers.<MemberUser>lambdaQuery()
                .eq(MemberUser::getSubToken, subToken));
    }

    /** 更新登录时间与 IP; 显式 set updated_at 因 Wrapper 更新不走 MetaObjectHandler. */
    default int updateLastLogin(String id, String loginIp, LocalDateTime loginAt) {
        return update(null, Wrappers.<MemberUser>lambdaUpdate()
                .set(MemberUser::getLastLoginAt, loginAt)
                .set(MemberUser::getLastLoginIp, loginIp)
                .set(MemberUser::getUpdatedAt, LocalDateTime.now())
                .eq(MemberUser::getId, id));
    }

    /** 单独更新密码哈希; 显式 set updated_at. */
    default int updatePasswordHash(String id, String passwordHash) {
        return update(null, Wrappers.<MemberUser>lambdaUpdate()
                .set(MemberUser::getPasswordHash, passwordHash)
                .set(MemberUser::getUpdatedAt, LocalDateTime.now())
                .eq(MemberUser::getId, id));
    }

    /** 重置 sub_token; 显式 set updated_at. */
    default int updateSubToken(String id, String newSubToken) {
        return update(null, Wrappers.<MemberUser>lambdaUpdate()
                .set(MemberUser::getSubToken, newSubToken)
                .set(MemberUser::getUpdatedAt, LocalDateTime.now())
                .eq(MemberUser::getId, id));
    }

    /** 修改状态 (admin 禁用 / 启用); 显式 set updated_at. */
    default int updateStatus(String id, Integer status) {
        return update(null, Wrappers.<MemberUser>lambdaUpdate()
                .set(MemberUser::getStatus, status)
                .set(MemberUser::getUpdatedAt, LocalDateTime.now())
                .eq(MemberUser::getId, id));
    }

    /** Admin 端列表分页; keyword 模糊匹配 email. */
    default IPage<MemberUser> selectPageByQuery(IPage<MemberUser> page, AdminMemberPageReqVO reqVO) {
        return selectPage(page, Wrappers.<MemberUser>lambdaQuery()
                .eq(ObjectUtil.isNotNull(reqVO.getStatus()), MemberUser::getStatus, reqVO.getStatus())
                .like(StrUtil.isNotBlank(reqVO.getKeyword()), MemberUser::getEmail, reqVO.getKeyword())
                .orderByDesc(MemberUser::getCreatedAt));
    }
}
