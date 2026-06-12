package com.nook.biz.member.service.admin.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.member.api.enums.MemberUserStatusEnum;
import com.nook.biz.member.controller.admin.vo.MemberPageReqVO;
import com.nook.biz.member.entity.MemberUser;
import com.nook.biz.member.mapper.MemberUserMapper;
import com.nook.biz.member.service.admin.MemberService;
import com.nook.biz.member.validator.MemberUserValidator;
import com.nook.common.web.response.PageResult;
import com.nook.framework.security.stp.StpMemberUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 管理后台 - 会员管理 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class MemberServiceImpl implements MemberService {

    @Resource
    private MemberUserMapper memberUserMapper;
    @Resource
    private MemberUserValidator memberUserValidator;
    @Resource
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    /** 订阅 URL 公网 base (= agent 回拉 backend 的公网地址; /portal/sub 也由它对外提供). */
    @Value("${nook.agent.backend-public-url:}")
    private String backendPublicUrl;

    @Override
    public PageResult<MemberUser> page(MemberPageReqVO reqVO) {
        IPage<MemberUser> result = memberUserMapper.selectPageByQuery(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), reqVO);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public MemberUser findById(String id) {
        return memberUserValidator.validateExists(id);
    }

    @Override
    public String getSubUrl(String id) {
        // 校验会员存在
        MemberUser member = memberUserValidator.validateExists(id);
        // 公网 base 拼会员 sub_token 成订阅地址
        String base = StrUtil.removeSuffix(StrUtil.trimToEmpty(backendPublicUrl), "/");
        return base + "/portal/sub/" + member.getSubToken();
    }

    @Override
    public void disable(String id) {
        // 校验会员存在
        memberUserValidator.validateExists(id);
        // 置为禁用
        memberUserMapper.updateStatus(id, MemberUserStatusEnum.DISABLED.getCode());
        // 踢出该会员所有 sa-token 会话, 已签发的 token 立即失效
        StpMemberUtil.stpLogic().kickout(id);
        log.info("[disable] 会员已禁用: memberId={}", id);
    }

    @Override
    public void enable(String id) {
        // 校验会员存在
        memberUserValidator.validateExists(id);
        // 置为正常
        memberUserMapper.updateStatus(id, MemberUserStatusEnum.NORMAL.getCode());
        log.info("[enable] 会员已启用: memberId={}", id);
    }

    @Override
    public void updateRemark(String id, String remark) {
        // 校验会员存在
        memberUserValidator.validateExists(id);
        // 更新备注
        memberUserMapper.updateRemark(id, remark);
    }

    @Override
    public void resetPassword(String id, String password) {
        // 校验会员存在 + 新密码强度
        memberUserValidator.validateExists(id);
        memberUserValidator.validatePasswordStrength(password);
        // 加密并更新密码
        String passwordHash = bCryptPasswordEncoder.encode(password);
        memberUserMapper.updatePasswordHash(id, passwordHash);
        // 重置后踢出该会员所有会话, 旧 token 立即失效
        StpMemberUtil.stpLogic().kickout(id);
        log.info("[resetPassword] 管理员重置会员密码: memberId={}", id);
    }
}
