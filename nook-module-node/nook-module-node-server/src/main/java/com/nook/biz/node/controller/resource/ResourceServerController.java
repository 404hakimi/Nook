package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerBillingRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerBillingUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCapacityRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCreateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCredentialRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCredentialUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerDnsRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerDnsUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerPageReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerRespVO;
import com.nook.biz.node.convert.resource.ResourceServerBillingConvert;
import com.nook.biz.node.convert.resource.ResourceServerCapacityConvert;
import com.nook.biz.node.convert.resource.ResourceServerConvert;
import com.nook.biz.node.convert.resource.ResourceServerCredentialConvert;
import com.nook.biz.node.convert.resource.ResourceServerDnsConvert;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.service.resource.ResourceServerBillingService;
import com.nook.biz.node.service.resource.ResourceServerCapacityService;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.biz.node.service.resource.ResourceServerDnsService;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.validator.ResourceServerValidator;
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

/**
 * 管理后台 - 资源服务器 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/server")
@Validated
@RequiredArgsConstructor
public class ResourceServerController {

    private final ResourceServerService resourceServerService;
    private final ResourceServerCredentialService credentialService;
    private final ResourceServerBillingService billingService;
    private final ResourceServerDnsService dnsService;
    private final ResourceServerCapacityService capacityService;
    private final ResourceServerValidator serverValidator;

    /** 创建 server (主表 + credential + billing + dns 一次性写). */
    @PostMapping("/create")
    public Result<ResourceServerRespVO> createServer(@Valid @RequestBody ResourceServerCreateReqVO createReqVO) {
        String id = resourceServerService.createServer(createReqVO);
        ResourceServerDO server = serverValidator.validateExists(id);
        return Result.ok(ResourceServerConvert.INSTANCE.convert(server));
    }

    /** 更新核心字段 (name/region/totalIp/maxClients/remark; lifecycle 走 /lifecycle). */
    @PutMapping("/{id}/core")
    public Result<Boolean> updateCore(@PathVariable("id") String id,
                                      @Valid @RequestBody ResourceServerCoreUpdateReqVO reqVO) {
        resourceServerService.updateCore(id, reqVO);
        return Result.ok(true);
    }

    /** 更新 SSH 凭据 (LIVE 后 host/port 硬锁; 密码空保留原值). */
    @PutMapping("/{id}/credential")
    public Result<Boolean> updateCredential(@PathVariable("id") String id,
                                            @Valid @RequestBody ResourceServerCredentialUpdateReqVO reqVO) {
        credentialService.update(id, reqVO);
        return Result.ok(true);
    }

    /** 更新账面 (idc/带宽/成本/账单日/到期日). */
    @PutMapping("/{id}/billing")
    public Result<Boolean> updateBilling(@PathVariable("id") String id,
                                         @Valid @RequestBody ResourceServerBillingUpdateReqVO reqVO) {
        billingService.update(id, reqVO);
        return Result.ok(true);
    }

    /** 更新 DNS 绑定 (domain/cfZoneId/cfRecordId). */
    @PutMapping("/{id}/dns")
    public Result<Boolean> updateDns(@PathVariable("id") String id,
                                     @Valid @RequestBody ResourceServerDnsUpdateReqVO reqVO) {
        dnsService.update(id, reqVO);
        return Result.ok(true);
    }

    @DeleteMapping("/delete")
    public Result<Boolean> deleteServer(@RequestParam("id") String id) {
        resourceServerService.deleteServer(id);
        return Result.ok(true);
    }

    /** 取 server 核心字段; SSH/账面/DNS 见配套子接口. */
    @GetMapping("/get")
    public Result<ResourceServerRespVO> getServer(@RequestParam("id") String id) {
        ResourceServerDO server = serverValidator.validateExists(id);
        return Result.ok(ResourceServerConvert.INSTANCE.convert(server));
    }

    /** 取 SSH 凭据 (UI 编辑 dialog prefill 用; 密码字段空着, 改密码才填). */
    @GetMapping("/{id}/credential")
    public Result<ResourceServerCredentialRespVO> getCredential(@PathVariable("id") String id) {
        serverValidator.validateExists(id);
        return Result.ok(ResourceServerCredentialConvert.INSTANCE.convert(credentialService.get(id)));
    }

    /** 取账面. */
    @GetMapping("/{id}/billing")
    public Result<ResourceServerBillingRespVO> getBilling(@PathVariable("id") String id) {
        serverValidator.validateExists(id);
        return Result.ok(ResourceServerBillingConvert.INSTANCE.convert(billingService.get(id)));
    }

    /** 取 DNS 绑定. */
    @GetMapping("/{id}/dns")
    public Result<ResourceServerDnsRespVO> getDns(@PathVariable("id") String id) {
        serverValidator.validateExists(id);
        return Result.ok(ResourceServerDnsConvert.INSTANCE.convert(dnsService.get(id)));
    }

    @GetMapping("/page")
    public Result<PageResult<ResourceServerRespVO>> getServerPage(@ModelAttribute ResourceServerPageReqVO pageReqVO) {
        PageResult<ResourceServerDO> pageResult = resourceServerService.getServerPage(pageReqVO);
        return Result.ok(ResourceServerConvert.INSTANCE.convertPage(pageResult));
    }

    /** 切换 lifecycle_state; admin 上线 / 退役流转用. */
    @PostMapping("/lifecycle")
    public Result<Boolean> transitionLifecycle(@RequestParam("id") String id,
                                               @RequestParam("state") String state) {
        resourceServerService.transitionLifecycle(id, state);
        return Result.ok(true);
    }

    /** 取 server 流量配额 + 已用 (监控面板用); 未上报过 NIC 时返 null. */
    @GetMapping("/capacity")
    public Result<ResourceServerCapacityRespVO> getCapacity(@RequestParam("id") String id) {
        serverValidator.validateExists(id);
        return Result.ok(ResourceServerCapacityConvert.INSTANCE.convert(capacityService.get(id)));
    }
}
