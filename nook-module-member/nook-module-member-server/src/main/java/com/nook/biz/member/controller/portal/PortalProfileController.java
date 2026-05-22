package com.nook.biz.member.controller.portal;

import com.nook.biz.member.controller.portal.vo.PortalChangePasswordReqVO;
import com.nook.biz.member.controller.portal.vo.PortalMemberRespVO;
import com.nook.biz.member.service.MemberProfileService;
import com.nook.common.web.response.Result;
import com.nook.framework.security.stp.StpMemberUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端 - 会员资料 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/portal/member/profile")
@RequiredArgsConstructor
public class PortalProfileController {

    private final MemberProfileService memberProfileService;

    /** 当前登录会员信息 (前端刷新页面后回填). */
    @GetMapping("/me")
    public Result<PortalMemberRespVO> me() {
        return Result.ok(memberProfileService.getProfile(StpMemberUtil.getLoginIdAsString()));
    }

    /** 修改密码. */
    @PostMapping("/password/change")
    public Result<Void> changePassword(@RequestBody @Valid PortalChangePasswordReqVO reqVO) {
        memberProfileService.changePassword(StpMemberUtil.getLoginIdAsString(), reqVO);
        return Result.ok();
    }

    /** 重置 sub_token; 返回新 token, 前端引导用户更新订阅 URL. */
    @PostMapping("/sub-token/reset")
    public Result<String> resetSubToken() {
        return Result.ok(memberProfileService.resetSubToken(StpMemberUtil.getLoginIdAsString()));
    }
}
