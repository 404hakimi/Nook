package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.ResourceIpPoolPageReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolSaveReqVO;
import com.nook.biz.node.convert.resource.ResourceIpPoolConvert;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolDO;
import com.nook.biz.node.service.resource.ResourceIpPoolService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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

/**
 * 管理后台 - IP 池
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/ip-pool")
@Validated
public class ResourceIpPoolController {

    @Resource
    private ResourceIpPoolService resourceIpPoolService;

    @PostMapping
    public Result<ResourceIpPoolRespVO> createIpPool(@Valid @RequestBody ResourceIpPoolSaveReqVO createReqVO) {
        String id = resourceIpPoolService.createIpPool(createReqVO);
        ResourceIpPoolDO ipPool = resourceIpPoolService.getIpPool(id);
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convert(ipPool));
    }

    @PutMapping("/{id}")
    public Result<Boolean> updateIpPool(@PathVariable("id") String id,
                                        @Valid @RequestBody ResourceIpPoolSaveReqVO updateReqVO) {
        resourceIpPoolService.updateIpPool(id, updateReqVO);
        return Result.ok(true);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteIpPool(@PathVariable("id") String id) {
        resourceIpPoolService.deleteIpPool(id);
        return Result.ok(true);
    }

    @GetMapping("/{id}")
    public Result<ResourceIpPoolRespVO> getIpPool(@PathVariable("id") String id) {
        ResourceIpPoolDO ipPool = resourceIpPoolService.getIpPool(id);
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convert(ipPool));
    }

    @GetMapping
    public Result<PageResult<ResourceIpPoolRespVO>> getIpPoolPage(@ModelAttribute ResourceIpPoolPageReqVO pageReqVO) {
        PageResult<ResourceIpPoolDO> pageResult = resourceIpPoolService.getIpPoolPage(pageReqVO);
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convertPage(pageResult));
    }

    /** 退订: occupied → cooling 状态切换; 回到 available 由调度器 sweep 完成. */
    @PostMapping("/{id}/release")
    public Result<Boolean> releaseIpPool(@PathVariable("id") String id) {
        resourceIpPoolService.releaseToCooling(id);
        return Result.ok(true);
    }
}
