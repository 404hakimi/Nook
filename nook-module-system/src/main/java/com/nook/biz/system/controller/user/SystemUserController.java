package com.nook.biz.system.controller.user;

import com.nook.biz.system.controller.user.vo.SystemUserCreateReqVO;
import com.nook.biz.system.controller.user.vo.SystemUserPageReqVO;
import com.nook.biz.system.controller.user.vo.SystemUserRespVO;
import com.nook.biz.system.controller.user.vo.SystemUserUpdatePasswordReqVO;
import com.nook.biz.system.controller.user.vo.SystemUserUpdateReqVO;
import com.nook.biz.system.convert.SystemUserConvert;
import com.nook.biz.system.service.SystemUserService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import com.nook.framework.security.stp.StpSystemUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用户管理接口; controller 仅做参数绑定 + 调 service, 校验由 service 注入的 Validator 在内部完成.
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/system/users")
public class SystemUserController {

    @Resource
    private SystemUserService systemUserService;

    @GetMapping
    public Result<PageResult<SystemUserRespVO>> page(@ModelAttribute SystemUserPageReqVO reqVO) {
        return Result.ok(SystemUserConvert.INSTANCE.convertPage(systemUserService.page(reqVO)));
    }

    @GetMapping("/{id}")
    public Result<SystemUserRespVO> detail(@PathVariable String id) {
        return Result.ok(SystemUserConvert.INSTANCE.convert(systemUserService.findById(id)));
    }

    @PostMapping
    public Result<SystemUserRespVO> create(@RequestBody @Valid SystemUserCreateReqVO reqVO) {
        return Result.ok(SystemUserConvert.INSTANCE.convert(systemUserService.create(reqVO)));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id,
                               @RequestBody @Valid SystemUserUpdateReqVO reqVO) {
        systemUserService.update(id, reqVO);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        systemUserService.delete(id, StpSystemUtil.getLoginIdAsString());
        return Result.ok();
    }

    @PutMapping("/{id}/password")
    public Result<Void> updatePassword(@PathVariable String id,
                                       @RequestBody @Valid SystemUserUpdatePasswordReqVO reqVO) {
        systemUserService.resetPassword(id, reqVO.getNewPassword());
        return Result.ok();
    }
}
