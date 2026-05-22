package com.nook.biz.member.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.member.controller.admin.vo.AdminMemberPageReqVO;
import com.nook.biz.member.entity.MemberUser;
import com.nook.biz.member.mapper.MemberUserMapper;
import com.nook.biz.member.service.AdminMemberService;
import com.nook.biz.member.validator.MemberUserValidator;
import com.nook.common.web.response.PageResult;
import com.nook.framework.security.stp.StpMemberUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 管理后台 - 会员管理 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMemberServiceImpl implements AdminMemberService {

    private final MemberUserMapper memberUserMapper;
    private final MemberUserValidator memberUserValidator;

    @Override
    public PageResult<MemberUser> page(AdminMemberPageReqVO reqVO) {
        IPage<MemberUser> result = memberUserMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), reqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public MemberUser findById(String id) {
        return memberUserValidator.validateExists(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(String id) {
        memberUserValidator.validateExists(id);
        memberUserMapper.updateStatus(id, 2);
        // 踢出所有该会员的 sa-token 会话, 已签发的 token 立即失效
        StpMemberUtil.stpLogic().kickout(id);
        log.info("[disable] 会员已禁用: memberId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(String id) {
        memberUserValidator.validateExists(id);
        memberUserMapper.updateStatus(id, 1);
        log.info("[enable] 会员已启用: memberId={}", id);
    }

    @Override
    public void updateRemark(String id, String remark) {
        memberUserValidator.validateExists(id);
        memberUserMapper.update(null, Wrappers.<MemberUser>lambdaUpdate()
                .set(MemberUser::getRemark, remark)
                .set(MemberUser::getUpdatedAt, LocalDateTime.now())
                .eq(MemberUser::getId, id));
    }
}
