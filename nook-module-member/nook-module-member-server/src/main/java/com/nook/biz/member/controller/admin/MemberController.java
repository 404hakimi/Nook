package com.nook.biz.member.controller.admin;

import com.nook.biz.member.controller.admin.vo.MemberPageReqVO;
import com.nook.biz.member.controller.admin.vo.MemberRespVO;
import com.nook.biz.member.controller.admin.vo.MemberResetPasswordReqVO;
import com.nook.biz.member.controller.admin.vo.MemberUpdateRemarkReqVO;
import com.nook.biz.member.convert.admin.MemberUserConvert;
import com.nook.biz.member.entity.MemberUser;
import com.nook.biz.member.service.admin.MemberService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 会员管理 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/member/user")
@Validated
public class MemberController {

    @Resource
    private MemberService memberService;

    /**
     * 获得会员分页列表
     *
     * @param reqVO 分页条件
     * @return 分页列表
     */
    @GetMapping("/page-user")
    public Result<PageResult<MemberRespVO>> page(@Valid MemberPageReqVO reqVO) {
        // 分页查询
        PageResult<MemberUser> page = memberService.page(reqVO);
        // 转换返回
        return Result.ok(MemberUserConvert.INSTANCE.convertPage(page));
    }

    /**
     * 获得会员详情
     *
     * @param id 会员ID
     * @return 会员详情
     */
    @GetMapping("/get-user")
    public Result<MemberRespVO> detail(@RequestParam("id") String id) {
        // 查询会员
        MemberUser member = memberService.findById(id);
        // 转换返回
        return Result.ok(MemberUserConvert.INSTANCE.convert(member));
    }

    /**
     * 获得会员订阅分享 URL (客户端导入)
     *
     * @param id 会员ID
     * @return 订阅分享 URL
     */
    @GetMapping("/get-sub-url")
    public Result<String> getSubUrl(@RequestParam("id") String id) {
        return Result.ok(memberService.getSubUrl(id));
    }

    /**
     * 禁用会员并踢出已有会话
     *
     * @param id 会员ID
     */
    @PostMapping("/disable-user")
    public Result<Void> disable(@RequestParam("id") String id) {
        memberService.disable(id);
        return Result.ok();
    }

    /**
     * 启用会员
     *
     * @param id 会员ID
     */
    @PostMapping("/enable-user")
    public Result<Void> enable(@RequestParam("id") String id) {
        memberService.enable(id);
        return Result.ok();
    }

    /**
     * 修改备注
     *
     * @param id    会员ID
     * @param reqVO 备注信息
     */
    @PutMapping("/update-remark")
    public Result<Void> updateRemark(@RequestParam("id") String id,
                                     @RequestBody @Valid MemberUpdateRemarkReqVO reqVO) {
        memberService.updateRemark(id, reqVO.getRemark());
        return Result.ok();
    }

    /**
     * 重置会员密码并踢出该会员已有会话
     *
     * @param id    会员ID
     * @param reqVO 新密码信息
     */
    @PutMapping("/reset-password")
    public Result<Void> resetPassword(@RequestParam("id") String id,
                                      @RequestBody @Valid MemberResetPasswordReqVO reqVO) {
        memberService.resetPassword(id, reqVO.getPassword());
        return Result.ok();
    }
}
