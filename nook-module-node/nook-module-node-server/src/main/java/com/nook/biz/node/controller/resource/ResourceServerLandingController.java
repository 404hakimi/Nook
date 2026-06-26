package com.nook.biz.node.controller.resource;

import com.nook.biz.node.controller.resource.vo.landing.ServerLandingBillingRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingBillingUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingQuotaRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingQuotaUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingCoreUpdateReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingDeployReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingInstallRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingListItemRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingPageReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingSocks5RespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingSocks5UpdateReqVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingSummaryRespVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.landing.Socks5TestReqVO;
import com.nook.biz.node.controller.resource.vo.landing.Socks5TestRespVO;
import com.nook.biz.node.convert.resource.ResourceServerLandingConvert;
import com.nook.biz.node.entity.ResourceServerBillingDO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.entity.ResourceServerQuotaDO;
import com.nook.biz.node.entity.ResourceServerRuntimeDO;
import com.nook.biz.node.entity.ResourceServerTrafficDO;
import com.nook.biz.node.entity.Socks5InstallDO;
import com.nook.biz.node.service.resource.ResourceServerLandingService;
import com.nook.biz.node.service.resource.ResourceServerLandingSocksOpsService;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.resource.ResourceServerTrafficService;
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
    @Resource
    private ResourceServerTrafficService resourceServerTrafficService;
    @Resource
    private ResourceServerService resourceServerService;

    /**
     * 获得落地节点分页
     *
     * @param reqVO 分页条件
     * @return 落地节点分页
     */
    @GetMapping("/page-landing")
    public Result<PageResult<ServerLandingListItemRespVO>> getPage(@ModelAttribute ServerLandingPageReqVO reqVO) {
        return Result.ok(resourceServerLandingService.getLandingPage(reqVO));
    }

    /**
     * 获得落地节点总览
     *
     * @return 总览统计
     */
    @GetMapping("/get-summary")
    public Result<ServerLandingSummaryRespVO> getSummary() {
        // 查询统计
        Map<String, Long> raw = resourceServerLandingService.getSummary();
        // 转换返回
        return Result.ok(ResourceServerLandingConvert.INSTANCE.toSummaryRespVO(raw));
    }

    /**
     * 获得落地节点详情
     *
     * @param id 落地节点编号
     * @return 落地节点详情
     */
    @GetMapping("/get-landing")
    public Result<ServerLandingRespVO> getDetail(@RequestParam("id") String id) {
        // 查询主表与各子表
        ResourceServerDO server = resourceServerLandingService.getServer(id);
        Socks5InstallDO landing = resourceServerLandingService.getLanding(id);
        ResourceServerBillingDO billing = resourceServerLandingService.getBilling(id);
        ResourceServerQuotaDO quota = resourceServerLandingService.getQuota(id);
        ResourceServerTrafficDO traffic = resourceServerTrafficService.getCurrent(id);
        ResourceServerRuntimeDO runtime = resourceServerLandingService.getRuntime(id);
        // 拼装详情返回
        return Result.ok(ResourceServerLandingConvert.INSTANCE.convertWithSubtables(
                server, landing, billing, quota, traffic, runtime));
    }

    /**
     * 删除落地节点
     *
     * @param id 落地节点编号
     */
    @DeleteMapping("/delete-landing")
    public Result<Void> delete(@RequestParam("id") String id) {
        // 落地机也是一条 resource_server, 走统一的级联删 + 绑定守卫
        resourceServerService.deleteServer(id);
        return Result.ok();
    }

    /**
     * 切换落地机生命周期 (上线 / 退役)
     *
     * @param id    落地机编号
     * @param state 目标生命周期
     */
    @PostMapping("/transition-lifecycle")
    public Result<Void> transitionLifecycle(@RequestParam("id") String id,
                                               @RequestParam("state") String state) {
        resourceServerLandingService.transitionLifecycle(id, state);
        return Result.ok();
    }

    /**
     * 更新核心字段
     *
     * @param id    落地节点编号
     * @param reqVO 核心字段入参
     */
    @PutMapping("/update-core")
    public Result<Void> updateCore(@RequestParam("id") String id,
                                      @Valid @RequestBody ServerLandingCoreUpdateReqVO reqVO) {
        resourceServerLandingService.updateCore(id, reqVO);
        return Result.ok();
    }

    /**
     * 获得 dante 配置
     *
     * @param id 落地节点编号
     * @return dante 配置
     */
    @GetMapping("/get-socks5")
    public Result<ServerLandingSocks5RespVO> getSocks5(@RequestParam("id") String id) {
        // 查询装机子表
        Socks5InstallDO landing = resourceServerLandingService.getLanding(id);
        // 转换返回
        return Result.ok(ResourceServerLandingConvert.INSTANCE.toSocks5RespVO(landing));
    }

    /**
     * 更新 dante 配置
     *
     * @param id    落地节点编号
     * @param reqVO dante 配置入参
     */
    @PutMapping("/update-socks5")
    public Result<Void> updateSocks5(@RequestParam("id") String id,
                                        @Valid @RequestBody ServerLandingSocks5UpdateReqVO reqVO) {
        resourceServerLandingService.updateSocks5(id, reqVO);
        return Result.ok();
    }

    /**
     * 获得装机事实
     *
     * @param id 落地节点编号
     * @return 装机事实
     */
    @GetMapping("/get-install")
    public Result<ServerLandingInstallRespVO> getInstall(@RequestParam("id") String id) {
        // 查询装机子表
        Socks5InstallDO landing = resourceServerLandingService.getLanding(id);
        // 转换返回
        return Result.ok(ResourceServerLandingConvert.INSTANCE.toInstallRespVO(landing));
    }

    /**
     * 获得账面
     *
     * @param id 落地节点编号
     * @return 账面 (子表不存在返回 null)
     */
    @GetMapping("/get-billing")
    public Result<ServerLandingBillingRespVO> getBilling(@RequestParam("id") String id) {
        // 查询账面子表
        ResourceServerBillingDO billing = resourceServerLandingService.getBilling(id);
        // 转换返回
        return Result.ok(ResourceServerLandingConvert.INSTANCE.toBillingRespVO(billing));
    }

    /**
     * 更新账面
     *
     * @param id    落地节点编号
     * @param reqVO 账面入参
     */
    @PutMapping("/update-billing")
    public Result<Void> updateBilling(@RequestParam("id") String id,
                                         @Valid @RequestBody ServerLandingBillingUpdateReqVO reqVO) {
        resourceServerLandingService.updateBilling(id, reqVO);
        return Result.ok();
    }

    /**
     * 获得配额监控
     *
     * @param id 落地节点编号
     * @return 配额监控 (子表不存在返回 null)
     */
    @GetMapping("/get-quota")
    public Result<ServerLandingQuotaRespVO> getQuota(@RequestParam("id") String id) {
        // 查询配额与当期流量
        ResourceServerQuotaDO quota = resourceServerLandingService.getQuota(id);
        ResourceServerTrafficDO traffic = resourceServerTrafficService.getCurrent(id);
        // 转换返回
        return Result.ok(ResourceServerLandingConvert.INSTANCE.toQuotaRespVO(quota, traffic));
    }

    /**
     * 更新配额阈值 (限速 / 月流量上限 / 重置策略)
     *
     * @param id    落地节点编号
     * @param reqVO 配额入参
     */
    @PutMapping("/update-quota")
    public Result<Void> updateQuota(@RequestParam("id") String id,
                                    @Valid @RequestBody ServerLandingQuotaUpdateReqVO reqVO) {
        resourceServerLandingService.updateQuota(id, reqVO);
        return Result.ok();
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
        Socks5TestRespVO respVO = resourceServerLandingSocksOpsService.testSocks5(id, reqVO.getEchoUrl(),
                reqVO.getConnectTimeoutMs(), reqVO.getReadTimeoutMs());
        return Result.ok(respVO);
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
