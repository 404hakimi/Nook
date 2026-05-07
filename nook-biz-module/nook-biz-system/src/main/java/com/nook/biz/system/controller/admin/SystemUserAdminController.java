package com.nook.biz.system.controller.admin;

import com.nook.biz.system.dto.CreateSystemUserDTO;
import com.nook.biz.system.dto.ResetPasswordDTO;
import com.nook.biz.system.dto.SystemUserQuery;
import com.nook.biz.system.dto.UpdateSystemUserDTO;
import com.nook.biz.system.entity.SystemUser;
import com.nook.biz.system.service.SystemUserService;
import com.nook.biz.system.vo.SystemUserVO;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
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
public class SystemUserAdminController {

    private final SystemUserService systemUserService;

    /** 分页查询；查询参数 page / size / keyword / status / role 见 SystemUserQuery。 */
    @GetMapping
    public Result<PageResult<SystemUserVO>> page(@ModelAttribute SystemUserQuery query) {
        PageResult<SystemUser> p = systemUserService.page(query);
        return Result.ok(p.map(SystemUserVO::from));
    }

    /** 详情。 */
    @GetMapping("/{id}")
    public Result<SystemUserVO> detail(@PathVariable @NotBlank String id) {
        return Result.ok(SystemUserVO.from(systemUserService.findById(id)));
    }

    /** 新增。 */
    @PostMapping
    public Result<SystemUserVO> create(@RequestBody @Valid CreateSystemUserDTO dto) {
        return Result.ok(SystemUserVO.from(systemUserService.create(dto)));
    }

    /** 更新基础信息(不含密码)。 */
    @PutMapping("/{id}")
    public Result<SystemUserVO> update(@PathVariable @NotBlank String id,
                                       @RequestBody @Valid UpdateSystemUserDTO dto) {
        return Result.ok(SystemUserVO.from(systemUserService.update(id, dto)));
    }

    /** 逻辑删除；不能删除当前登录用户。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @NotBlank String id) {
        systemUserService.delete(id, StpSystemUtil.getLoginIdAsString());
        return Result.ok();
    }

    /** 重置密码；不需要旧密码(超管行为)。 */
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable @NotBlank String id,
                                      @RequestBody @Valid ResetPasswordDTO dto) {
        systemUserService.resetPassword(id, dto.getNewPassword());
        return Result.ok();
    }
}
