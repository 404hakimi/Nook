package com.nook.biz.system.controller.region;

import com.nook.biz.system.controller.region.vo.SystemRegionCreateReqVO;
import com.nook.biz.system.controller.region.vo.SystemRegionRecodeReqVO;
import com.nook.biz.system.controller.region.vo.SystemRegionRespVO;
import com.nook.biz.system.controller.region.vo.SystemRegionUpdateReqVO;
import com.nook.biz.system.convert.region.SystemRegionConvert;
import com.nook.biz.system.entity.SystemRegionDO;
import com.nook.biz.system.service.SystemRegionService;
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

import java.util.List;

/**
 * 管理后台 - 区域字典 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/system/region")
@Validated
public class SystemRegionController {

    @Resource
    private SystemRegionService systemRegionService;

    /**
     * 获得已启用区域列表
     *
     * @return 已启用区域列表
     */
    @GetMapping("/list-enabled-region")
    public Result<List<SystemRegionRespVO>> listEnabled() {
        // 查询列表
        List<SystemRegionDO> regions = systemRegionService.listEnabled();
        // 转换返回
        return Result.ok(SystemRegionConvert.INSTANCE.convertList(regions));
    }

    /**
     * 获得区域全量列表
     *
     * @param keyword 关键字 (模糊匹配)
     * @param enabled 启用状态过滤; null=不过滤
     * @return 区域列表
     */
    @GetMapping("/list-region")
    public Result<List<SystemRegionRespVO>> list(@RequestParam(value = "keyword", required = false) String keyword,
                                                 @RequestParam(value = "enabled", required = false) Integer enabled) {
        // 查询列表
        List<SystemRegionDO> regions = systemRegionService.list(keyword, enabled);
        // 转换返回
        return Result.ok(SystemRegionConvert.INSTANCE.convertList(regions));
    }

    /**
     * 创建区域
     *
     * @param reqVO 创建信息
     * @return 区域码
     */
    @PostMapping("/create-region")
    public Result<String> create(@RequestBody @Valid SystemRegionCreateReqVO reqVO) {
        return Result.ok(systemRegionService.create(reqVO));
    }

    /**
     * 更新区域展示信息 (区域码不可改)
     *
     * @param code  区域码
     * @param reqVO 更新信息
     */
    @PutMapping("/update-region")
    public Result<Void> update(@RequestParam("code") String code,
                               @RequestBody @Valid SystemRegionUpdateReqVO reqVO) {
        systemRegionService.update(code, reqVO);
        return Result.ok();
    }

    /**
     * 更正区域码并级联迁移引用
     *
     * @param reqVO 更正信息
     */
    @PostMapping("/recode-region")
    public Result<Void> recode(@RequestBody @Valid SystemRegionRecodeReqVO reqVO) {
        systemRegionService.recode(reqVO);
        return Result.ok();
    }

    /**
     * 启用 / 停用区域
     *
     * @param code    区域码
     * @param enabled 是否启用
     */
    @PostMapping("/update-region-enabled")
    public Result<Void> toggleEnabled(@RequestParam("code") String code,
                                      @RequestParam("enabled") boolean enabled) {
        systemRegionService.toggleEnabled(code, enabled);
        return Result.ok();
    }
}
