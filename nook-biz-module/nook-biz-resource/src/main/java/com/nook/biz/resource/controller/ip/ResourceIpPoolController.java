package com.nook.biz.resource.controller.ip;

import com.nook.biz.resource.controller.ip.vo.ResourceIpPoolPageReqVO;
import com.nook.biz.resource.controller.ip.vo.ResourceIpPoolRespVO;
import com.nook.biz.resource.controller.ip.vo.ResourceIpPoolSaveReqVO;
import com.nook.biz.resource.convert.ResourceIpPoolConvert;
import com.nook.biz.resource.service.ResourceIpPoolService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/admin/resource/ip-pool")
@RequiredArgsConstructor
public class ResourceIpPoolController {

    private final ResourceIpPoolService resourceIpPoolService;

    @GetMapping
    public Result<PageResult<ResourceIpPoolRespVO>> page(@ModelAttribute ResourceIpPoolPageReqVO reqVO) {
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convertPage(resourceIpPoolService.page(reqVO)));
    }

    @GetMapping("/{id}")
    public Result<ResourceIpPoolRespVO> detail(@PathVariable String id) {
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convert(resourceIpPoolService.findById(id)));
    }

    @PostMapping
    public Result<ResourceIpPoolRespVO> create(@RequestBody @Valid ResourceIpPoolSaveReqVO reqVO) {
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convert(resourceIpPoolService.create(reqVO)));
    }

    @PutMapping("/{id}")
    public Result<ResourceIpPoolRespVO> update(@PathVariable String id,
                                               @RequestBody @Valid ResourceIpPoolSaveReqVO reqVO) {
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convert(resourceIpPoolService.update(id, reqVO)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        resourceIpPoolService.delete(id);
        return Result.ok();
    }

    /** 退订: 仅状态机切换 (occupied → cooling); 真正回到 available 由调度器 sweep 完成。 */
    @PostMapping("/{id}/release")
    public Result<Void> release(@PathVariable String id) {
        resourceIpPoolService.releaseToCooling(id);
        return Result.ok();
    }
}
