package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.ServerLandingBillingRespVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingBillingUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingCapacityRespVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingCapacityUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingDeployReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingInstallRespVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingPageReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingRespVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingSocks5RespVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingSocks5UpdateReqVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingSummaryRespVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.Socks5TestReqVO;
import com.nook.biz.node.controller.resource.vo.Socks5TestRespVO;
import com.nook.biz.node.convert.resource.ResourceServerLandingConvert;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.framework.socks5.probe.Socks5ProbeSnapshot;
import com.nook.biz.node.service.resource.ResourceServerLandingService;
import com.nook.biz.node.service.resource.ResourceServerLandingSocksOpsService;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
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
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.Map;
import java.util.Set;

/**
 * 管理后台 - SOCKS5 落地节点 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/resource/server-landing")
@Validated
public class ResourceServerLandingController {

    @Resource
    private ResourceServerLandingService resourceServerLandingService;
    @Resource
    private ResourceServerLandingSocksOpsService resourceServerLandingSocksOpsService;

    /**
     * 获得落地节点分页
     *
     * @param reqVO 分页条件
     * @return 落地节点分页
     */
    @GetMapping("/page-landing")
    public Result<PageResult<ServerLandingRespVO>> getPage(@ModelAttribute ServerLandingPageReqVO reqVO) {
        PageResult<ResourceServerDO> page = resourceServerLandingService.getPage(reqVO);
        Set<String> ids = CollectionUtils.convertSet(page.getRecords(), ResourceServerDO::getId);
        ResourceServerLandingService.SubtablesBundle bundle = resourceServerLandingService.batchLoadSubtables(ids);
        return Result.ok(ResourceServerLandingConvert.INSTANCE.convertPageWithSubtables(
                page, bundle.landings(), bundle.billings(),
                bundle.capacities(), bundle.runtimes()));
    }

    /**
     * 获得落地节点总览
     *
     * @return 总览统计
     */
    @GetMapping("/get-summary")
    public Result<ServerLandingSummaryRespVO> getSummary() {
        Map<String, Long> raw = resourceServerLandingService.getSummary();
        ServerLandingSummaryRespVO vo = new ServerLandingSummaryRespVO();
        vo.setTotal(raw.getOrDefault("total", 0L));
        vo.setInstalling(raw.getOrDefault("lifecycle_INSTALLING", 0L));
        vo.setReady(raw.getOrDefault("lifecycle_READY", 0L));
        vo.setLive(raw.getOrDefault("lifecycle_LIVE", 0L));
        vo.setRetired(raw.getOrDefault("lifecycle_RETIRED", 0L));
        vo.setAvailable(raw.getOrDefault("status_AVAILABLE", 0L));
        vo.setOccupied(raw.getOrDefault("status_OCCUPIED", 0L));
        vo.setReserved(raw.getOrDefault("status_RESERVED", 0L));
        return Result.ok(vo);
    }

    /**
     * 获得落地节点详情
     *
     * @param id 落地节点编号
     * @return 落地节点详情
     */
    @GetMapping("/get-landing")
    public Result<ServerLandingRespVO> getDetail(@RequestParam("id") String id) {
        return Result.ok(ResourceServerLandingConvert.INSTANCE.convertWithSubtables(
                resourceServerLandingService.getServer(id),
                resourceServerLandingService.getLanding(id),
                resourceServerLandingService.getBilling(id),
                resourceServerLandingService.getCapacity(id),
                resourceServerLandingService.getRuntime(id)));
    }

    /**
     * 删除落地节点
     *
     * @param id 落地节点编号
     * @return 是否成功
     */
    @DeleteMapping("/delete-landing")
    public Result<Boolean> delete(@RequestParam("id") String id) {
        resourceServerLandingService.delete(id);
        return Result.ok(true);
    }

    /**
     * 更新核心字段
     *
     * @param id    落地节点编号
     * @param reqVO 核心字段入参
     * @return 是否成功
     */
    @PutMapping("/update-core")
    public Result<Boolean> updateCore(@RequestParam("id") String id,
                                      @Valid @RequestBody ServerLandingCoreUpdateReqVO reqVO) {
        resourceServerLandingService.updateCore(id, reqVO);
        return Result.ok(true);
    }

    /**
     * 获得 dante 配置
     *
     * @param id 落地节点编号
     * @return dante 配置
     */
    @GetMapping("/get-socks5")
    public Result<ServerLandingSocks5RespVO> getSocks5(@RequestParam("id") String id) {
        return Result.ok(ResourceServerLandingConvert.INSTANCE.toSocks5RespVO(resourceServerLandingService.getLanding(id)));
    }

    /**
     * 更新 dante 配置
     *
     * @param id    落地节点编号
     * @param reqVO dante 配置入参
     * @return 是否成功
     */
    @PutMapping("/update-socks5")
    public Result<Boolean> updateSocks5(@RequestParam("id") String id,
                                        @Valid @RequestBody ServerLandingSocks5UpdateReqVO reqVO) {
        resourceServerLandingService.updateSocks5(id, reqVO);
        return Result.ok(true);
    }

    /**
     * 获得装机事实
     *
     * @param id 落地节点编号
     * @return 装机事实
     */
    @GetMapping("/get-install")
    public Result<ServerLandingInstallRespVO> getInstall(@RequestParam("id") String id) {
        return Result.ok(ResourceServerLandingConvert.INSTANCE.toInstallRespVO(resourceServerLandingService.getLanding(id)));
    }

    /**
     * 获得账面
     *
     * @param id 落地节点编号
     * @return 账面 (子表不存在返回 null)
     */
    @GetMapping("/get-billing")
    public Result<ServerLandingBillingRespVO> getBilling(@RequestParam("id") String id) {
        return Result.ok(ResourceServerLandingConvert.INSTANCE.toBillingRespVO(resourceServerLandingService.getBilling(id)));
    }

    /**
     * 更新账面
     *
     * @param id    落地节点编号
     * @param reqVO 账面入参
     * @return 是否成功
     */
    @PutMapping("/update-billing")
    public Result<Boolean> updateBilling(@RequestParam("id") String id,
                                         @Valid @RequestBody ServerLandingBillingUpdateReqVO reqVO) {
        resourceServerLandingService.updateBilling(id, reqVO);
        return Result.ok(true);
    }

    /**
     * 获得容量监控
     *
     * @param id 落地节点编号
     * @return 容量监控 (子表不存在返回 null)
     */
    @GetMapping("/get-capacity")
    public Result<ServerLandingCapacityRespVO> getCapacity(@RequestParam("id") String id) {
        return Result.ok(ResourceServerLandingConvert.INSTANCE.toCapacityRespVO(resourceServerLandingService.getCapacity(id)));
    }

    /**
     * 更新容量阈值 (限速 / 月流量上限 / 重置策略)
     *
     * @param id    落地节点编号
     * @param reqVO 容量入参
     * @return 是否成功
     */
    @PutMapping("/update-capacity")
    public Result<Boolean> updateCapacity(@RequestParam("id") String id,
                                          @Valid @RequestBody ServerLandingCapacityUpdateReqVO reqVO) {
        resourceServerLandingService.updateCapacity(id, reqVO);
        return Result.ok(true);
    }

    /**
     * 切 SOCKS5 开机自启
     *
     * @param id      落地节点编号
     * @param enabled 是否启用
     * @return 是否成功
     */
    @PostMapping("/set-socks5-autostart")
    public Result<Boolean> setSocks5Autostart(@RequestParam("id") String id,
                                              @RequestParam("enabled") boolean enabled) {
        resourceServerLandingSocksOpsService.setAutostart(id, enabled);
        return Result.ok(true);
    }

    /**
     * 获得 SOCKS5 journal 日志
     *
     * @param id      落地节点编号
     * @param lines   行数
     * @param level   级别过滤
     * @param keyword 关键词过滤
     * @return 日志
     */
    @GetMapping("/get-socks5-log")
    public Result<ServiceLogRespVO> getSocks5Log(@RequestParam("id") String id,
                                                 @RequestParam(value = "lines", required = false) Integer lines,
                                                 @RequestParam(value = "level", required = false) String level,
                                                 @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(resourceServerLandingSocksOpsService.getJournalLog(id, lines, level, keyword));
    }

    /**
     * 获得 SOCKS5 自身日志文件
     *
     * @param id      落地节点编号
     * @param lines   行数
     * @param keyword 关键词过滤
     * @return 日志
     */
    @GetMapping("/get-socks5-log-file")
    public Result<ServiceLogRespVO> getSocks5LogFile(@RequestParam("id") String id,
                                                     @RequestParam(value = "lines", required = false) Integer lines,
                                                     @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(resourceServerLandingSocksOpsService.getFileLog(id, lines, keyword));
    }

    /**
     * 拨号测试 SOCKS5
     *
     * @param id    落地节点编号
     * @param reqVO 拨号入参
     * @return 拨号结果
     */
    @PostMapping("/test-socks5-dial")
    public Result<Socks5TestRespVO> testSocks5(@RequestParam("id") String id,
                                               @Valid @RequestBody Socks5TestReqVO reqVO) {
        Socks5ProbeSnapshot snap = resourceServerLandingSocksOpsService.testSocks5(id, reqVO.getEchoUrl(),
                reqVO.getConnectTimeoutMs(), reqVO.getReadTimeoutMs());
        Socks5TestRespVO vo = new Socks5TestRespVO();
        vo.setSuccess(snap.isSuccess());
        vo.setElapsedMs(snap.getElapsedMs());
        vo.setEchoUrl(snap.getEchoUrl());
        vo.setConnectTimeoutMs(snap.getConnectTimeoutMs());
        vo.setReadTimeoutMs(snap.getReadTimeoutMs());
        vo.setHttpStatus(snap.getHttpStatus());
        vo.setRawResponse(snap.getRawResponse());
        vo.setError(snap.getErrorMessage());
        return Result.ok(vo);
    }

    /**
     * 流式装机 SOCKS5
     *
     * @param id    落地节点编号
     * @param reqVO 装机配置 (install 路径 + 开关; 前端 prefill 默认值, 用户可改)
     * @return 流式响应
     */
    @PostMapping(value = "/install-socks5", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseBodyEmitter installSocks5(@RequestParam("id") String id,
                                             @Valid @RequestBody ServerLandingDeployReqVO reqVO) {
        return resourceServerLandingSocksOpsService.installSocks5Stream(id, reqVO);
    }

}
