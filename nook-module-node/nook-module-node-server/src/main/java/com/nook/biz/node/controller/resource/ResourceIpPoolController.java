package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.ResourceIpPoolPageReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolSaveReqVO;
import com.nook.biz.node.convert.resource.ResourceIpPoolConvert;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolDO;
import com.nook.biz.node.service.resource.ResourceIpPoolService;
import com.nook.biz.node.validator.ResourceIpPoolValidator;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 管理后台 - IP 池
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/ip-pool")
@Validated
@RequiredArgsConstructor
public class ResourceIpPoolController {

    private final ResourceIpPoolService resourceIpPoolService;
    private final ResourceIpPoolValidator ipPoolValidator;

    @PostMapping("/create")
    public Result<ResourceIpPoolRespVO> createIpPool(@Valid @RequestBody ResourceIpPoolSaveReqVO createReqVO) {
        String id = resourceIpPoolService.createIpPool(createReqVO);
        return Result.ok(loadDetail(id));
    }

    @PutMapping("/update")
    public Result<Boolean> updateIpPool(@RequestParam("id") String id,
                                        @Valid @RequestBody ResourceIpPoolSaveReqVO updateReqVO) {
        resourceIpPoolService.updateIpPool(id, updateReqVO);
        return Result.ok(true);
    }

    @DeleteMapping("/delete")
    public Result<Boolean> deleteIpPool(@RequestParam("id") String id) {
        resourceIpPoolService.deleteIpPool(id);
        return Result.ok(true);
    }

    @GetMapping("/get")
    public Result<ResourceIpPoolRespVO> getIpPool(@RequestParam("id") String id) {
        ipPoolValidator.validateExists(id);
        return Result.ok(loadDetail(id));
    }

    @GetMapping("/page")
    public Result<PageResult<ResourceIpPoolRespVO>> getIpPoolPage(@ModelAttribute ResourceIpPoolPageReqVO pageReqVO) {
        PageResult<ResourceIpPoolDO> pageResult = resourceIpPoolService.getIpPoolPage(pageReqVO);
        java.util.Set<String> ids = com.nook.common.utils.collection.CollectionUtils.convertSet(
                pageResult.getRecords(), ResourceIpPoolDO::getId);
        ResourceIpPoolService.SubtablesBundle bundle = resourceIpPoolService.batchLoadSubtables(ids);
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convertPageWithSubtables(
                pageResult, bundle.credentials(), bundle.billings(), bundle.socks5s(), bundle.runtimes()));
    }

    /** 单 IP 详情: 主 + 4 子表组装. */
    private ResourceIpPoolRespVO loadDetail(String id) {
        ResourceIpPoolDO main = ipPoolValidator.validateExists(id);
        return ResourceIpPoolConvert.INSTANCE.convertWithSubtables(main,
                resourceIpPoolService.getCredential(id),
                resourceIpPoolService.getBilling(id),
                resourceIpPoolService.getSocks5(id),
                resourceIpPoolService.getRuntime(id));
    }

    /** 退订: occupied → cooling 状态切换; 回到 available 由调度器 sweep 完成 */
    @PostMapping("/release")
    public Result<Boolean> releaseIpPool(@RequestParam("id") String id) {
        resourceIpPoolService.releaseToCooling(id);
        return Result.ok(true);
    }

    /** 切换 lifecycle_state; admin 上线 / 退役流转用. */
    @PostMapping("/lifecycle")
    public Result<Boolean> transitionLifecycle(@RequestParam("id") String id,
                                               @RequestParam("state") String state) {
        resourceIpPoolService.transitionLifecycle(id, state);
        return Result.ok(true);
    }
}
