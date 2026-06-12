package com.nook.biz.member.mapper;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.member.controller.admin.vo.MemberPageReqVO;
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

    default MemberUser selectByEmail(String email) {
        return selectOne(Wrappers.<MemberUser>lambdaQuery()
                .eq(MemberUser::getEmail, email));
    }

    default MemberUser selectBySubToken(String subToken) {
        return selectOne(Wrappers.<MemberUser>lambdaQuery()
                .eq(MemberUser::getSubToken, subToken));
    }

    default boolean existsByEmail(String email) {
        return exists(Wrappers.<MemberUser>lambdaQuery()
                .eq(MemberUser::getEmail, email));
    }

    default int updateLastLogin(String id, String loginIp, LocalDateTime loginAt) {
        return update(null, Wrappers.<MemberUser>lambdaUpdate()
                .set(MemberUser::getLastLoginAt, loginAt)
                .set(MemberUser::getLastLoginIp, loginIp)
                .set(MemberUser::getUpdatedAt, LocalDateTime.now())
                .eq(MemberUser::getId, id));
    }

    default int updatePasswordHash(String id, String passwordHash) {
        return update(null, Wrappers.<MemberUser>lambdaUpdate()
                .set(MemberUser::getPasswordHash, passwordHash)
                .set(MemberUser::getUpdatedAt, LocalDateTime.now())
                .eq(MemberUser::getId, id));
    }

    default int updateRemark(String id, String remark) {
        return update(null, Wrappers.<MemberUser>lambdaUpdate()
                .set(MemberUser::getRemark, remark)
                .set(MemberUser::getUpdatedAt, LocalDateTime.now())
                .eq(MemberUser::getId, id));
    }

    default int updateSubToken(String id, String newSubToken) {
        return update(null, Wrappers.<MemberUser>lambdaUpdate()
                .set(MemberUser::getSubToken, newSubToken)
                .set(MemberUser::getUpdatedAt, LocalDateTime.now())
                .eq(MemberUser::getId, id));
    }

    default int updateStatus(String id, Integer status) {
        return update(null, Wrappers.<MemberUser>lambdaUpdate()
                .set(MemberUser::getStatus, status)
                .set(MemberUser::getUpdatedAt, LocalDateTime.now())
                .eq(MemberUser::getId, id));
    }

    default IPage<MemberUser> selectPageByQuery(IPage<MemberUser> page, MemberPageReqVO reqVO) {
        return selectPage(page, Wrappers.<MemberUser>lambdaQuery()
                .eq(ObjectUtil.isNotNull(reqVO.getStatus()), MemberUser::getStatus, reqVO.getStatus())
                .like(StrUtil.isNotBlank(reqVO.getKeyword()), MemberUser::getEmail, reqVO.getKeyword())
                .orderByDesc(MemberUser::getCreatedAt));
    }
}
