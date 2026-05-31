package com.nook.biz.system.controller.region;

import com.nook.biz.system.controller.region.vo.SystemRegionRecodeReqVO;
import com.nook.biz.system.controller.region.vo.SystemRegionRespVO;
import com.nook.biz.system.controller.region.vo.SystemRegionSaveReqVO;
import com.nook.biz.system.dal.dataobject.region.SystemRegionDO;
import com.nook.biz.system.service.region.SystemRegionService;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class SystemRegionController {

    private final SystemRegionService systemRegionService;

    /**
     * 获得已启用区域列表 (表单下拉用)
     *
     * @return 已启用区域列表
     */
    @GetMapping("/list-enabled-region")
    public Result<List<SystemRegionRespVO>> listEnabled() {
        List<SystemRegionDO> list = systemRegionService.listEnabled();
        return Result.ok(CollectionUtils.convertList(list, e -> BeanUtils.toBean(e, SystemRegionRespVO.class)));
    }

    /**
     * 获得区域全量列表 (admin 管理用; 支持关键字 + 启用状态过滤)
     *
     * @param keyword 关键字
     * @param enabled 启用状态
     * @return 区域列表
     */
    @GetMapping("/list-region")
    public Result<List<SystemRegionRespVO>> list(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Integer enabled) {
        List<SystemRegionDO> list = systemRegionService.list(keyword, enabled);
        return Result.ok(CollectionUtils.convertList(list, e -> BeanUtils.toBean(e, SystemRegionRespVO.class)));
    }

    /** 新增区域 */
    @PostMapping("/create-region")
    public Result<String> create(@Valid @RequestBody SystemRegionSaveReqVO reqVO) {
        return Result.ok(systemRegionService.create(reqVO));
    }

    /** 编辑区域（区域码不可改） */
    @PutMapping("/update-region")
    public Result<Boolean> update(@Valid @RequestBody SystemRegionSaveReqVO reqVO) {
        systemRegionService.update(reqVO);
        return Result.ok(true);
    }

    /** 更正区域码（改主键 + 级联迁移引用该码的线路机/落地机和套餐） */
    @PostMapping("/recode-region")
    public Result<Boolean> recode(@Valid @RequestBody SystemRegionRecodeReqVO reqVO) {
        systemRegionService.recode(reqVO);
        return Result.ok(true);
    }

    /** 启用 / 停用区域 */
    @PostMapping("/update-region-enabled")
    public Result<Boolean> toggleEnabled(@RequestParam String code, @RequestParam boolean enabled) {
        systemRegionService.toggleEnabled(code, enabled);
        return Result.ok(true);
    }
}
