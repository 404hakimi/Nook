package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.ResourceServerBillingRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerBillingUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerQuotaRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerQuotaUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCreateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCredentialRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerCredentialUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceServerRespVO;
import com.nook.biz.node.controller.resource.vo.frontline.ServerFrontlineListItemRespVO;
import com.nook.biz.node.entity.ResourceServerBillingDO;
import com.nook.biz.node.entity.ResourceServerCredentialDO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.entity.ResourceServerQuotaDO;
import com.nook.biz.node.entity.ResourceServerTrafficDO;
import com.nook.biz.node.convert.resource.ResourceServerBillingConvert;
import com.nook.biz.node.convert.resource.ResourceServerQuotaConvert;
import com.nook.biz.node.convert.resource.ResourceServerConvert;
import com.nook.biz.node.convert.resource.ResourceServerCredentialConvert;
import com.nook.biz.node.service.resource.ResourceServerBillingService;
import com.nook.biz.node.service.resource.ResourceServerQuotaService;
import com.nook.biz.node.service.resource.ResourceServerTrafficService;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.biz.node.service.resource.ResourceServerFrontlineService;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理后台 - 服务器公共 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/server")
@Validated
public class ResourceServerController {

    @Resource
    private ResourceServerService resourceServerService;
    @Resource
    private ResourceServerCredentialService resourceServerCredentialService;
    @Resource
    private ResourceServerBillingService resourceServerBillingService;
    @Resource
    private ResourceServerQuotaService resourceServerQuotaService;
    @Resource
    private ResourceServerTrafficService resourceServerTrafficService;
    @Resource
    private ResourceServerFrontlineService resourceServerFrontlineService;

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
     * 按区域统计机器数 (线路机 + 落地机)
     *
     * @return 区域码 → 机器数
     */
    @GetMapping("/get-region-usage")
    public Result<Map<String, Long>> getRegionUsage() {
        return Result.ok(resourceServerService.countByRegion());
    }

    /**
     * 获得 server 核心字段
     *
     * @param id server 编号
     * @return server 核心字段
     */
    @GetMapping("/get-server")
    public Result<ResourceServerRespVO> getServer(@RequestParam("id") String id) {
        // 查询服务器
        ResourceServerDO server = resourceServerService.requireServer(id);
        // 转换返回
        return Result.ok(ResourceServerConvert.INSTANCE.convert(server));
    }

    /**
     * 获得服务器详情 + agent 运行时聚合 (线路机 / 落地机共用)
     *
     * @param id server 编号
     * @return 服务器详情 + 运行时聚合
     */
    @GetMapping("/get-detail-with-runtime")
    public Result<ServerFrontlineListItemRespVO> getDetailWithRuntime(@RequestParam("id") String id) {
        resourceServerService.requireServer(id);
        return Result.ok(resourceServerFrontlineService.getServerRuntimeDetail(id));
    }

    /**
     * 更新核心字段
     *
     * @param id    server 编号
     * @param reqVO 核心字段更新入参
     */
    @PutMapping("/update-core")
    public Result<Void> updateCore(@RequestParam("id") String id,
                                      @Valid @RequestBody ResourceServerCoreUpdateReqVO reqVO) {
        resourceServerService.updateCore(id, reqVO);
        return Result.ok();
    }

    /**
     * 删除 server
     *
     * @param id server 编号
     */
    @DeleteMapping("/delete-server")
    public Result<Void> deleteServer(@RequestParam("id") String id) {
        resourceServerService.deleteServer(id);
        return Result.ok();
    }

    /**
     * 获得 SSH 凭据
     *
     * @param id server 编号
     * @return SSH 凭据
     */
    @GetMapping("/get-credential")
    public Result<ResourceServerCredentialRespVO> getCredential(@RequestParam("id") String id) {
        // 校验服务器是否存在
        resourceServerService.requireServer(id);
        // 获取服务器凭证
        ResourceServerCredentialDO serverCredential = resourceServerCredentialService.getServerCredential(id);
        // 视图转换返回凭证
        return Result.ok(ResourceServerCredentialConvert.INSTANCE.convert(serverCredential));
    }

    /**
     * 更新 SSH 凭据
     *
     * @param id    server 编号
     * @param reqVO 凭据更新入参
     */
    @PutMapping("/update-credential")
    public Result<Void> updateCredential(@RequestParam("id") String id,
                                            @Valid @RequestBody ResourceServerCredentialUpdateReqVO reqVO) {
        resourceServerCredentialService.update(id, reqVO);
        return Result.ok();
    }

    /**
     * 获得账面
     *
     * @param id server 编号
     * @return 账面
     */
    @GetMapping("/get-billing")
    public Result<ResourceServerBillingRespVO> getBilling(@RequestParam("id") String id) {
        // 校验服务器是否存在
        resourceServerService.requireServer(id);
        // 查询账面
        ResourceServerBillingDO billing = resourceServerBillingService.get(id);
        // 转换返回
        return Result.ok(ResourceServerBillingConvert.INSTANCE.convert(billing));
    }

    /**
     * 更新账面
     *
     * @param id    server 编号
     * @param reqVO 账面更新入参
     */
    @PutMapping("/update-billing")
    public Result<Void> updateBilling(@RequestParam("id") String id,
                                         @Valid @RequestBody ResourceServerBillingUpdateReqVO reqVO) {
        resourceServerBillingService.update(id, reqVO);
        return Result.ok();
    }

    /**
     * 获得配额
     *
     * @param id server 编号
     * @return 配额
     */
    @GetMapping("/get-quota")
    public Result<ResourceServerQuotaRespVO> getQuota(@RequestParam("id") String id) {
        // 校验服务器是否存在
        resourceServerService.requireServer(id);
        // 查询配额与当期流量
        ResourceServerQuotaDO quota = resourceServerQuotaService.get(id);
        ResourceServerTrafficDO traffic = resourceServerTrafficService.getCurrent(id);
        // 转换返回
        return Result.ok(ResourceServerQuotaConvert.INSTANCE.convert(quota, traffic));
    }

    /**
     * 更新配额阈值
     *
     * @param id    server 编号
     * @param reqVO 配额更新入参
     */
    @PutMapping("/update-quota")
    public Result<Void> updateQuota(@RequestParam("id") String id,
                                       @RequestBody @Valid ResourceServerQuotaUpdateReqVO reqVO) {
        resourceServerService.requireServer(id);
        resourceServerQuotaService.updateQuota(id, reqVO);
        return Result.ok();
    }
}
