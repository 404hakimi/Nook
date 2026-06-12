package com.nook.biz.system.controller.user;

import com.nook.biz.system.controller.user.vo.SystemUserCreateReqVO;
import com.nook.biz.system.controller.user.vo.SystemUserPageReqVO;
import com.nook.biz.system.controller.user.vo.SystemUserRespVO;
import com.nook.biz.system.controller.user.vo.SystemUserUpdatePasswordReqVO;
import com.nook.biz.system.controller.user.vo.SystemUserUpdateReqVO;
import com.nook.biz.system.convert.user.SystemUserConvert;
import com.nook.biz.system.entity.SystemUser;
import com.nook.biz.system.service.SystemUserService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import com.nook.framework.security.stp.StpSystemUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 后台用户 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/system/user")
@Validated
public class SystemUserController {

    @Resource
    private SystemUserService systemUserService;

    /**
     * 获得用户分页列表
     *
     * @param reqVO 分页条件
     * @return 分页列表
     */
    @GetMapping("/page-user")
    public Result<PageResult<SystemUserRespVO>> getUserPage(@Valid SystemUserPageReqVO reqVO) {
        // 分页查询
        PageResult<SystemUser> page = systemUserService.page(reqVO);
        // 转换返回
        return Result.ok(SystemUserConvert.INSTANCE.convertPage(page));
    }

    /**
     * 获得用户详情
     *
     * @param id 用户ID
     * @return 用户详情
     */
    @GetMapping("/get-user")
    public Result<SystemUserRespVO> getUser(@RequestParam("id") String id) {
        // 查询用户
        SystemUser user = systemUserService.findById(id);
        // 转换返回
        return Result.ok(SystemUserConvert.INSTANCE.convert(user));
    }

    /**
     * 创建用户
     *
     * @param reqVO 创建信息
     * @return 用户详情
     */
    @PostMapping("/create-user")
    public Result<SystemUserRespVO> createUser(@RequestBody @Valid SystemUserCreateReqVO reqVO) {
        // 创建用户
        SystemUser user = systemUserService.create(reqVO);
        // 转换返回
        return Result.ok(SystemUserConvert.INSTANCE.convert(user));
    }

    /**
     * 修改用户基础信息
     *
     * @param id    用户ID
     * @param reqVO 更新信息
     */
    @PutMapping("/update-user")
    public Result<Void> updateUser(@RequestParam("id") String id,
                                   @RequestBody @Valid SystemUserUpdateReqVO reqVO) {
        systemUserService.update(id, reqVO);
        return Result.ok();
    }

    /**
     * 删除用户
     *
     * @param id 用户ID
     */
    @DeleteMapping("/delete-user")
    public Result<Void> deleteUser(@RequestParam("id") String id) {
        systemUserService.delete(id, StpSystemUtil.getLoginIdAsString());
        return Result.ok();
    }

    /**
     * 重置用户密码
     *
     * @param id    用户ID
     * @param reqVO 新密码信息
     */
    @PutMapping("/reset-password")
    public Result<Void> resetPassword(@RequestParam("id") String id,
                                      @RequestBody @Valid SystemUserUpdatePasswordReqVO reqVO) {
        systemUserService.resetPassword(id, reqVO.getNewPassword());
        return Result.ok();
    }
}
