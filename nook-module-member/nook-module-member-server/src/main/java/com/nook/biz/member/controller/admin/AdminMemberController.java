package com.nook.biz.member.controller.admin;

import com.nook.biz.member.controller.admin.vo.AdminMemberPageReqVO;
import com.nook.biz.member.controller.admin.vo.AdminMemberRespVO;
import com.nook.biz.member.controller.admin.vo.AdminMemberUpdateRemarkReqVO;
import com.nook.biz.member.convert.MemberUserConvert;
import com.nook.biz.member.entity.MemberUser;
import com.nook.biz.member.service.AdminMemberService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/admin/member/users")
@RequiredArgsConstructor
@Validated
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    /** 会员列表分页. */
    @GetMapping("/page-user")
    public Result<PageResult<AdminMemberRespVO>> page(@Valid AdminMemberPageReqVO reqVO) {
        return Result.ok(MemberUserConvert.INSTANCE.convertAdminPage(adminMemberService.page(reqVO)));
    }

    /** 会员详情. */
    @GetMapping("/get-user")
    public Result<AdminMemberRespVO> detail(@RequestParam("id") String id) {
        MemberUser member = adminMemberService.findById(id);
        return Result.ok(MemberUserConvert.INSTANCE.convertAdmin(member));
    }

    /** 禁用会员; 同时踢出已有会话. */
    @PostMapping("/disable-user")
    public Result<Void> disable(@RequestParam("id") String id) {
        adminMemberService.disable(id);
        return Result.ok();
    }

    /** 启用会员. */
    @PostMapping("/enable-user")
    public Result<Void> enable(@RequestParam("id") String id) {
        adminMemberService.enable(id);
        return Result.ok();
    }

    /** 修改备注. */
    @PutMapping("/update-remark")
    public Result<Void> updateRemark(@RequestParam("id") String id,
                                     @RequestBody @Valid AdminMemberUpdateRemarkReqVO reqVO) {
        adminMemberService.updateRemark(id, reqVO.getRemark());
        return Result.ok();
    }
}
