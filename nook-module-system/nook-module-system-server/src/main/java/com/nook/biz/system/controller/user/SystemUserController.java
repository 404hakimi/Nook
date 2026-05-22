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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 系统用户
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/system/user")
@Validated
public class SystemUserController {

    @Resource
    private SystemUserService systemUserService;

    @GetMapping("/page")
    public Result<PageResult<SystemUserRespVO>> getUserPage(@ModelAttribute SystemUserPageReqVO pageReqVO) {
        return Result.ok(SystemUserConvert.INSTANCE.convertPage(systemUserService.page(pageReqVO)));
    }

    @GetMapping("/get")
    public Result<SystemUserRespVO> getUser(@RequestParam("id") String id) {
        return Result.ok(SystemUserConvert.INSTANCE.convert(systemUserService.findById(id)));
    }

    @PostMapping("/create")
    public Result<SystemUserRespVO> createUser(@Valid @RequestBody SystemUserCreateReqVO createReqVO) {
        return Result.ok(SystemUserConvert.INSTANCE.convert(systemUserService.create(createReqVO)));
    }

    @PutMapping("/update")
    public Result<Boolean> updateUser(@RequestParam("id") String id,
                                      @Valid @RequestBody SystemUserUpdateReqVO updateReqVO) {
        systemUserService.update(id, updateReqVO);
        return Result.ok(true);
    }

    @DeleteMapping("/delete")
    public Result<Boolean> deleteUser(@RequestParam("id") String id) {
        systemUserService.delete(id, StpSystemUtil.getLoginIdAsString());
        return Result.ok(true);
    }

    @PutMapping("/reset-password")
    public Result<Boolean> resetPassword(@RequestParam("id") String id,
                                         @Valid @RequestBody SystemUserUpdatePasswordReqVO updateReqVO) {
        systemUserService.resetPassword(id, updateReqVO.getNewPassword());
        return Result.ok(true);
    }
}
