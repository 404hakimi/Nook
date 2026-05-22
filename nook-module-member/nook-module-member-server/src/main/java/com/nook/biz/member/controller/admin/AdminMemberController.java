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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @GetMapping("/page")
    public Result<PageResult<AdminMemberRespVO>> page(@Valid AdminMemberPageReqVO reqVO) {
        return Result.ok(MemberUserConvert.INSTANCE.convertAdminPage(adminMemberService.page(reqVO)));
    }

    /** 会员详情. */
    @GetMapping("/{id}")
    public Result<AdminMemberRespVO> detail(@PathVariable String id) {
        MemberUser member = adminMemberService.findById(id);
        return Result.ok(MemberUserConvert.INSTANCE.convertAdmin(member));
    }

    /** 禁用会员; 同时踢出已有会话. */
    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable String id) {
        adminMemberService.disable(id);
        return Result.ok();
    }

    /** 启用会员. */
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable String id) {
        adminMemberService.enable(id);
        return Result.ok();
    }

    /** 修改备注. */
    @PutMapping("/{id}/remark")
    public Result<Void> updateRemark(@PathVariable String id,
                                     @RequestBody @Valid AdminMemberUpdateRemarkReqVO reqVO) {
        adminMemberService.updateRemark(id, reqVO.getRemark());
        return Result.ok();
    }
}
