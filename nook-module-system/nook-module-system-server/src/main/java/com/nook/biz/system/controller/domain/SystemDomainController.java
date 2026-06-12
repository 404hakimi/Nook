package com.nook.biz.system.controller.domain;

import com.nook.biz.system.controller.domain.vo.SystemDomainCreateReqVO;
import com.nook.biz.system.controller.domain.vo.SystemDomainRespVO;
import com.nook.biz.system.controller.domain.vo.SystemDomainUpdateReqVO;
import com.nook.biz.system.convert.domain.SystemDomainConvert;
import com.nook.biz.system.entity.SystemDomainDO;
import com.nook.biz.system.service.SystemDomainService;
import com.nook.common.web.response.Result;
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

import java.util.List;

/**
 * 管理后台 - 系统域名 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/system/domain")
@Validated
public class SystemDomainController {

    @Resource
    private SystemDomainService systemDomainService;

    /**
     * 获得域名列表
     *
     * @return 域名列表
     */
    @GetMapping("/list-domain")
    public Result<List<SystemDomainRespVO>> listDomain() {
        // 查询列表
        List<SystemDomainDO> domains = systemDomainService.getDomainList();
        // 转换返回
        return Result.ok(SystemDomainConvert.INSTANCE.convertList(domains));
    }

    /**
     * 获得域名详情
     *
     * @param id 域名ID
     * @return 域名详情
     */
    @GetMapping("/get-domain")
    public Result<SystemDomainRespVO> getDomain(@RequestParam("id") String id) {
        // 查询域名
        SystemDomainDO domain = systemDomainService.getDomain(id);
        // 转换返回
        return Result.ok(SystemDomainConvert.INSTANCE.convert(domain));
    }

    /**
     * 创建域名
     *
     * @param reqVO 创建信息
     * @return 域名ID
     */
    @PostMapping("/create-domain")
    public Result<String> createDomain(@RequestBody @Valid SystemDomainCreateReqVO reqVO) {
        return Result.ok(systemDomainService.createDomain(reqVO));
    }

    /**
     * 更新域名
     *
     * @param id    域名ID
     * @param reqVO 更新信息
     */
    @PutMapping("/update-domain")
    public Result<Void> updateDomain(@RequestParam("id") String id,
                                     @RequestBody @Valid SystemDomainUpdateReqVO reqVO) {
        systemDomainService.updateDomain(id, reqVO);
        return Result.ok();
    }

    /**
     * 删除域名
     *
     * @param id 域名ID
     */
    @DeleteMapping("/delete-domain")
    public Result<Void> deleteDomain(@RequestParam("id") String id) {
        systemDomainService.deleteDomain(id);
        return Result.ok();
    }
}
