package com.nook.biz.system.controller.domain;

import com.nook.biz.system.controller.domain.vo.SystemDomainRespVO;
import com.nook.biz.system.controller.domain.vo.SystemDomainSaveReqVO;
import com.nook.biz.system.convert.SystemDomainConvert;
import com.nook.biz.system.service.domain.SystemDomainService;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台 - 系统域名 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/system/domain")
@Validated
@RequiredArgsConstructor
public class SystemDomainController {

    private final SystemDomainService systemDomainService;

    /** 域名列表. */
    @GetMapping("/list-domain")
    public Result<List<SystemDomainRespVO>> listDomain() {
        return Result.ok(SystemDomainConvert.INSTANCE.convertList(systemDomainService.getDomainList()));
    }

    /** 域名详情. */
    @GetMapping("/get-domain")
    public Result<SystemDomainRespVO> getDomain(@RequestParam("id") String id) {
        return Result.ok(SystemDomainConvert.INSTANCE.convert(systemDomainService.getDomain(id)));
    }

    /** 创建域名. */
    @PostMapping("/create-domain")
    public Result<String> createDomain(@Valid @RequestBody SystemDomainSaveReqVO reqVO) {
        return Result.ok(systemDomainService.createDomain(reqVO));
    }

    /** 更新域名. */
    @PutMapping("/update-domain")
    public Result<Boolean> updateDomain(@Valid @RequestBody SystemDomainSaveReqVO reqVO) {
        systemDomainService.updateDomain(reqVO);
        return Result.ok(true);
    }

    /** 删除域名. */
    @DeleteMapping("/delete-domain")
    public Result<Boolean> deleteDomain(@RequestParam("id") String id) {
        systemDomainService.deleteDomain(id);
        return Result.ok(true);
    }
}
