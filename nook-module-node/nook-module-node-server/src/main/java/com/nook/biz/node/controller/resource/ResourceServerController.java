package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerBillingRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerBillingUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCapacityRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCapacityUpdateReqVO;
import com.nook.biz.agent.api.AgentRuntimeConfigApi;
import com.nook.biz.node.controller.resource.vo.ResourceServerCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCreateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCredentialRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCredentialUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerRespVO;
import com.nook.biz.node.controller.resource.vo.ServerFrontlineListItemRespVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.convert.resource.ResourceServerBillingConvert;
import com.nook.biz.node.convert.resource.ResourceServerCapacityConvert;
import com.nook.biz.node.convert.resource.ResourceServerConvert;
import com.nook.biz.node.convert.resource.ResourceServerCredentialConvert;
import com.nook.biz.node.convert.resource.ResourceServerFrontlineConvert;
import com.nook.biz.node.service.resource.ResourceServerBillingService;
import com.nook.biz.node.service.resource.ResourceServerCapacityService;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.biz.node.service.resource.ResourceServerFrontlineService;
import com.nook.biz.node.service.resource.ResourceServerService;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 管理后台 - 服务器公共 Controller
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
    private final ResourceServerCapacityService capacityService;
    private final ResourceServerFrontlineService frontlineService;
    private final AgentRuntimeConfigApi agentRuntimeConfigApi;

    /**
     * 创建服务器 (frontline / landing 共用; 按 serverType 分发)
     *
     * @param reqVO 创建入参
     * @return 服务器主表
     */
    @PostMapping("/create-server")
    public Result<ResourceServerRespVO> createServer(@Valid @RequestBody ResourceServerCreateReqVO reqVO) {
        String id = resourceServerService.createServer(reqVO);
        ResourceServerDO server = resourceServerService.requireServer(id);
        return Result.ok(ResourceServerConvert.INSTANCE.convert(server));
    }

    /**
     * 获得 server 核心字段
     *
     * @param id server 编号
     * @return server 核心字段
     */
    @GetMapping("/get-server")
    public Result<ResourceServerRespVO> getServer(@RequestParam("id") String id) {
        return Result.ok(ResourceServerConvert.INSTANCE.convert(resourceServerService.requireServer(id)));
    }

    /**
     * 获得 server 详情 + agent 运行时聚合 (frontline / landing 共用; 详情页 header + Agent tab 用)
     *
     * @param id server 编号
     * @return 主表 + credential / runtime / capacity / xray + configSyncState 拼装结果
     */
    @GetMapping("/get-detail-with-runtime")
    public Result<ServerFrontlineListItemRespVO> getDetailWithRuntime(@RequestParam("id") String id) {
        ResourceServerDO server = resourceServerService.requireServer(id);
        ResourceServerFrontlineService.RuntimeBundle bundle = frontlineService.loadRuntimeBundleSingle(id);
        Map<String, String> cfgSyncMap = agentRuntimeConfigApi.getSyncStateMap(Set.of(id));
        return Result.ok(ResourceServerFrontlineConvert.INSTANCE.convertSingleWithRuntime(
                server,
                bundle.credentialMap().get(id),
                bundle.runtimeMap().get(id),
                bundle.capacityMap().get(id),
                bundle.xrayMap().get(id),
                cfgSyncMap.get(id),
                LocalDateTime.now()));
    }

    /**
     * 更新核心字段
     *
     * @param id    server 编号
     * @param reqVO 核心字段更新入参
     * @return 是否成功
     */
    @PutMapping("/update-core")
    public Result<Boolean> updateCore(@RequestParam("id") String id,
                                      @Valid @RequestBody ResourceServerCoreUpdateReqVO reqVO) {
        resourceServerService.updateCore(id, reqVO);
        return Result.ok(true);
    }

    /**
     * 删除 server
     *
     * @param id server 编号
     * @return 是否成功
     */
    @DeleteMapping("/delete-server")
    public Result<Boolean> deleteServer(@RequestParam("id") String id) {
        resourceServerService.deleteServer(id);
        return Result.ok(true);
    }

    /**
     * 切换 lifecycle_state
     *
     * @param id    server 编号
     * @param state 目标 lifecycle 状态
     * @return 是否成功
     */
    @PostMapping("/transition-lifecycle")
    public Result<Boolean> transitionLifecycle(@RequestParam("id") String id,
                                               @RequestParam("state") String state) {
        resourceServerService.transitionLifecycle(id, state);
        return Result.ok(true);
    }

    /**
     * 获得 SSH 凭据
     *
     * @param id server 编号
     * @return SSH 凭据
     */
    @GetMapping("/get-credential")
    public Result<ResourceServerCredentialRespVO> getCredential(@RequestParam("id") String id) {
        resourceServerService.requireServer(id);
        return Result.ok(ResourceServerCredentialConvert.INSTANCE.convert(credentialService.get(id)));
    }

    /**
     * 更新 SSH 凭据
     *
     * @param id    server 编号
     * @param reqVO 凭据更新入参
     * @return 是否成功
     */
    @PutMapping("/update-credential")
    public Result<Boolean> updateCredential(@RequestParam("id") String id,
                                            @Valid @RequestBody ResourceServerCredentialUpdateReqVO reqVO) {
        credentialService.update(id, reqVO);
        return Result.ok(true);
    }

    /**
     * 获得账面
     *
     * @param id server 编号
     * @return 账面
     */
    @GetMapping("/get-billing")
    public Result<ResourceServerBillingRespVO> getBilling(@RequestParam("id") String id) {
        resourceServerService.requireServer(id);
        return Result.ok(ResourceServerBillingConvert.INSTANCE.convert(billingService.get(id)));
    }

    /**
     * 更新账面
     *
     * @param id    server 编号
     * @param reqVO 账面更新入参
     * @return 是否成功
     */
    @PutMapping("/update-billing")
    public Result<Boolean> updateBilling(@RequestParam("id") String id,
                                         @Valid @RequestBody ResourceServerBillingUpdateReqVO reqVO) {
        billingService.update(id, reqVO);
        return Result.ok(true);
    }

    /**
     * 获得容量
     *
     * @param id server 编号
     * @return 容量
     */
    @GetMapping("/get-capacity")
    public Result<ResourceServerCapacityRespVO> getCapacity(@RequestParam("id") String id) {
        resourceServerService.requireServer(id);
        return Result.ok(ResourceServerCapacityConvert.INSTANCE.convert(capacityService.get(id)));
    }

    /**
     * 更新容量阈值
     *
     * @param id    server 编号
     * @param reqVO 容量更新入参
     * @return 是否成功
     */
    @PutMapping("/update-capacity")
    public Result<Boolean> updateCapacity(@RequestParam("id") String id,
                                          @RequestBody @Valid ResourceServerCapacityUpdateReqVO reqVO) {
        resourceServerService.requireServer(id);
        capacityService.updateQuota(id, reqVO);
        return Result.ok(true);
    }
}
