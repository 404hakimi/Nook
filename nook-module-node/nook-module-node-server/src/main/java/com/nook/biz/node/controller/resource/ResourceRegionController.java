package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.ResourceRegionRespVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceRegionDO;
import com.nook.biz.node.service.resource.ResourceRegionService;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台 - 资源区域 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/region")
@RequiredArgsConstructor
public class ResourceRegionController {

    private final ResourceRegionService resourceRegionService;

    /**
     * 获得已启用区域列表 (表单下拉用)
     *
     * @return 已启用区域列表
     */
    @GetMapping("/enabled")
    public Result<List<ResourceRegionRespVO>> listEnabled() {
        List<ResourceRegionDO> list = resourceRegionService.listEnabled();
        return Result.ok(CollectionUtils.convertList(list, e -> BeanUtils.toBean(e, ResourceRegionRespVO.class)));
    }

    /**
     * 获得区域全量列表 (admin 管理用; 支持关键字 + 启用状态过滤)
     *
     * @param keyword 关键字
     * @param enabled 启用状态
     * @return 区域列表
     */
    @GetMapping("/list")
    public Result<List<ResourceRegionRespVO>> list(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Integer enabled) {
        List<ResourceRegionDO> list = resourceRegionService.list(keyword, enabled);
        return Result.ok(CollectionUtils.convertList(list, e -> BeanUtils.toBean(e, ResourceRegionRespVO.class)));
    }
}
