package com.nook.biz.member.controller.portal;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.member.controller.portal.vo.PortalChangePasswordReqVO;
import com.nook.biz.member.controller.portal.vo.PortalMemberRespVO;
import com.nook.biz.member.convert.portal.PortalMemberUserConvert;
import com.nook.biz.member.entity.MemberUser;
import com.nook.biz.member.service.portal.PortalMemberService;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.error.ErrorCode;
import com.nook.common.web.response.Result;
import com.nook.framework.security.stp.StpMemberUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/portal/member/user")
@Validated
public class PortalMemberController {

    @Resource
    private PortalMemberService portalMemberService;

    /**
     * 获取当前登录会员信息
     *
     * @return 会员信息
     */
    @GetMapping("/get-member-info")
    public Result<PortalMemberRespVO> getMemberInfo() {
        // 查询当前登录会员
        String memberId = StpMemberUtil.getLoginIdAsString();
        if (StrUtil.isBlank(memberId)) {
            return Result.fail(CommonErrorCode.UNAUTHORIZED);
        }
        MemberUser memberUser = portalMemberService.getMemberUser(memberId);
        // 转换返回
        PortalMemberRespVO memberUserVo = PortalMemberUserConvert.INSTANCE.convert(memberUser);
        return Result.ok(memberUserVo);
    }

    /**
     * 修改密码
     *
     * @param reqVO 修改密码信息
     */
    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody @Valid PortalChangePasswordReqVO reqVO) {
        portalMemberService.changePassword(StpMemberUtil.getLoginIdAsString(), reqVO);
        return Result.ok();
    }

    /**
     * 重置订阅 token
     *
     * @return 新订阅 token
     */
    @PostMapping("/reset-sub-token")
    public Result<String> resetSubToken() {
        return Result.ok(portalMemberService.resetSubToken(StpMemberUtil.getLoginIdAsString()));
    }
}
