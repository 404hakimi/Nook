package com.nook.biz.system.controller.user;

import com.nook.biz.system.controller.user.vo.SystemUserPageReqVO;
import com.nook.biz.system.controller.user.vo.SystemUserRespVO;
import com.nook.biz.system.controller.user.vo.SystemUserSaveReqVO;
import com.nook.biz.system.controller.user.vo.SystemUserUpdatePasswordReqVO;
import com.nook.biz.system.convert.SystemUserConvert;
import com.nook.biz.system.service.SystemUserService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import com.nook.common.web.validation.Create;
import com.nook.common.web.validation.Update;
import com.nook.framework.security.stp.StpSystemUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/system/users")
@RequiredArgsConstructor
@Validated
public class SystemUserController {

    private final SystemUserService systemUserService;

    /** 分页查询；查询参数 pageNo / pageSize / keyword / status / role 见 SystemUserPageReqVO。 */
    @GetMapping
    public Result<PageResult<SystemUserRespVO>> page(@ModelAttribute SystemUserPageReqVO reqVO) {
        return Result.ok(SystemUserConvert.INSTANCE.convertPage(systemUserService.page(reqVO)));
    }

    /** 详情。 */
    @GetMapping("/{id}")
    public Result<SystemUserRespVO> detail(@PathVariable @NotBlank String id) {
        return Result.ok(SystemUserConvert.INSTANCE.convert(systemUserService.findById(id)));
    }

    /** 新增；username/password/role 必填。 */
    @PostMapping
    public Result<SystemUserRespVO> create(@RequestBody @Validated(Create.class) SystemUserSaveReqVO reqVO) {
        return Result.ok(SystemUserConvert.INSTANCE.convert(systemUserService.create(reqVO)));
    }

    /** 编辑；username/password 在编辑场景下被 Service 忽略(密码走单独接口)。 */
    @PutMapping("/{id}")
    public Result<SystemUserRespVO> update(@PathVariable @NotBlank String id,
                                           @RequestBody @Validated(Update.class) SystemUserSaveReqVO reqVO) {
        return Result.ok(SystemUserConvert.INSTANCE.convert(systemUserService.update(id, reqVO)));
    }

    /** 逻辑删除；不能删除当前登录用户。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @NotBlank String id) {
        systemUserService.delete(id, StpSystemUtil.getLoginIdAsString());
        return Result.ok();
    }

    /** 重置密码；不需要旧密码(超管行为)。 */
    @PutMapping("/{id}/password")
    public Result<Void> updatePassword(@PathVariable @NotBlank String id,
                                       @RequestBody @Valid SystemUserUpdatePasswordReqVO reqVO) {
        systemUserService.resetPassword(id, reqVO.getNewPassword());
        return Result.ok();
    }
}
