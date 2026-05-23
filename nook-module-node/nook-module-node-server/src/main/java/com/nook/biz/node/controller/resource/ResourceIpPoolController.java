package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.ResourceIpPoolBillingRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolBillingUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolCredentialRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolCredentialUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolPageReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolSaveReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolSocks5RespVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpPoolSocks5UpdateReqVO;
import com.nook.biz.node.convert.resource.ResourceIpPoolConvert;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolDO;
import com.nook.biz.node.service.resource.ResourceIpPoolBillingService;
import com.nook.biz.node.service.resource.ResourceIpPoolCredentialService;
import com.nook.biz.node.service.resource.ResourceIpPoolService;
import com.nook.biz.node.service.resource.ResourceIpPoolSocks5Service;
import com.nook.biz.node.validator.ResourceIpPoolValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 管理后台 - IP 池 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/ip-pool")
@Validated
@RequiredArgsConstructor
public class ResourceIpPoolController {

    private final ResourceIpPoolService resourceIpPoolService;
    private final ResourceIpPoolCredentialService credentialService;
    private final ResourceIpPoolBillingService billingService;
    private final ResourceIpPoolSocks5Service socks5Service;
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
        Set<String> ids = CollectionUtils.convertSet(pageResult.getRecords(), ResourceIpPoolDO::getId);
        ResourceIpPoolService.SubtablesBundle bundle = resourceIpPoolService.batchLoadSubtables(ids);
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convertPageWithSubtables(
                pageResult, bundle.credentials(), bundle.billings(), bundle.socks5s(), bundle.runtimes()));
    }

    /** 退订: occupied → cooling 状态切换; 回到 available 由调度器 sweep 完成. */
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

    // ===== 子表分段编辑 endpoint (admin 拆 4 个对话框各自调) =====

    /** 更新核心字段 (region/ipTypeId/ipAddress/provisionMode/remark; lifecycle 走 /lifecycle). */
    @PutMapping("/{id}/core")
    public Result<Boolean> updateCore(@PathVariable("id") String id,
                                      @Valid @RequestBody ResourceIpPoolCoreUpdateReqVO reqVO) {
        resourceIpPoolService.updateCore(id, reqVO);
        return Result.ok(true);
    }

    /** 取 SSH 凭据 (编辑 dialog prefill; 密码字段空着, 改密码才填). */
    @GetMapping("/{id}/credential")
    public Result<ResourceIpPoolCredentialRespVO> getCredential(@PathVariable("id") String id) {
        ipPoolValidator.validateExists(id);
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convertCredential(credentialService.get(id)));
    }

    /** 更新 SSH 凭据 (sshPassword 留空 = 保留原值). */
    @PutMapping("/{id}/credential")
    public Result<Boolean> updateCredential(@PathVariable("id") String id,
                                            @Valid @RequestBody ResourceIpPoolCredentialUpdateReqVO reqVO) {
        credentialService.update(id, reqVO);
        return Result.ok(true);
    }

    /** 取账面. */
    @GetMapping("/{id}/billing")
    public Result<ResourceIpPoolBillingRespVO> getBilling(@PathVariable("id") String id) {
        ipPoolValidator.validateExists(id);
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convertBilling(billingService.get(id)));
    }

    /** 更新账面. */
    @PutMapping("/{id}/billing")
    public Result<Boolean> updateBilling(@PathVariable("id") String id,
                                         @Valid @RequestBody ResourceIpPoolBillingUpdateReqVO reqVO) {
        billingService.update(id, reqVO);
        return Result.ok(true);
    }

    /** 取 dante 配置 + 限速. */
    @GetMapping("/{id}/socks5")
    public Result<ResourceIpPoolSocks5RespVO> getSocks5(@PathVariable("id") String id) {
        ipPoolValidator.validateExists(id);
        return Result.ok(ResourceIpPoolConvert.INSTANCE.convertSocks5(socks5Service.get(id)));
    }

    /** 更新 dante 配置 + 限速 (socks5Password 留空 = 保留原值; 改限速触发链路校验). */
    @PutMapping("/{id}/socks5")
    public Result<Boolean> updateSocks5(@PathVariable("id") String id,
                                        @Valid @RequestBody ResourceIpPoolSocks5UpdateReqVO reqVO) {
        socks5Service.update(id, reqVO);
        return Result.ok(true);
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
}
