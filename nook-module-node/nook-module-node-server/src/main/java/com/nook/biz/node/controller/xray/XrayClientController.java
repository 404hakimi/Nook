package com.nook.biz.node.controller.xray;

import com.nook.biz.node.controller.xray.vo.XrayClientCredentialRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientPageReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientProvisionReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientTrafficRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientReplayReportRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientSyncStatusRespVO;
import com.nook.biz.node.convert.xray.XrayClientConvert;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.service.resource.ResourceIpPoolService;
import com.nook.biz.node.service.resource.ResourceServerCredentialService;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.xray.client.XrayClientService;
import com.nook.biz.node.service.xray.client.XrayClientTrafficService;
import com.nook.biz.node.service.xray.config.XrayConfigService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理后台 - Xray 客户端 Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/xray/client")
@Validated
@RequiredArgsConstructor
public class XrayClientController {

    private final XrayClientService xrayClientService;
    private final XrayClientTrafficService xrayClientTrafficService;
    private final ResourceIpPoolService resourceIpPoolService;
    private final ResourceServerService resourceServerService;
    private final ResourceServerCredentialService credentialService;
    private final XrayConfigService xrayConfigService;

    /**
     * 获得 xray 客户端分页
     *
     * @param pageReqVO 分页条件
     * @return 客户端分页 (含 ipAddress / serverName / inbound 字段)
     */
    @GetMapping("/page")
    public Result<PageResult<XrayClientRespVO>> getXrayClientPage(@ModelAttribute XrayClientPageReqVO pageReqVO) {
        PageResult<XrayClientDO> pageResult = xrayClientService.getXrayClientPage(pageReqVO);
        Set<String> serverIds = XrayClientConvert.collectServerIds(pageResult.getRecords());
        Map<String, String> ipMap = loadIpAddressMap(XrayClientConvert.collectIpIds(pageResult.getRecords()));
        Map<String, ResourceServerDO> serverMap = loadServerMap(serverIds);
        Map<String, String> hostMap = loadHostMap(serverIds);
        Map<String, XrayConfigDO> configMap = loadConfigMap(serverIds);
        return Result.ok(XrayClientConvert.INSTANCE.convertPage(pageResult, ipMap, serverMap, hostMap, configMap));
    }

    /**
     * 获得 xray 客户端详情
     *
     * @param id 客户端编号
     * @return 客户端详情
     */
    @GetMapping("/get")
    public Result<XrayClientRespVO> getXrayClient(@RequestParam("id") String id) {
        XrayClientDO entity = xrayClientService.getXrayClient(id);
        return Result.ok(convertOne(entity));
    }

    /**
     * 开通 xray 客户端 (provision)
     *
     * @param createReqVO 开通入参
     * @return 客户端详情
     */
    @PostMapping("/create")
    public Result<XrayClientRespVO> createXrayClient(@Valid @RequestBody XrayClientProvisionReqVO createReqVO) {
        XrayClientDO client = xrayClientService.provisionXrayClient(createReqVO);
        return Result.ok(convertOne(client));
    }

    /**
     * 吊销 xray 客户端 (远端 inbound + DB 双删)
     *
     * @param id 客户端编号
     * @return 是否成功
     */
    @DeleteMapping("/delete")
    public Result<Boolean> deleteXrayClient(@RequestParam("id") String id) {
        xrayClientService.revokeXrayClient(id);
        return Result.ok(true);
    }

    /**
     * 轮换客户端 UUID / 密钥 (老凭据立即失效)
     *
     * @param id 客户端编号
     * @return 客户端详情 (新 UUID)
     */
    @PostMapping("/rotate")
    public Result<XrayClientRespVO> rotateXrayClient(@RequestParam("id") String id) {
        XrayClientDO entity = xrayClientService.rotateXrayClient(id);
        return Result.ok(convertOne(entity));
    }

    /**
     * 获得客户端实时流量 (上 / 下 / 已用 / 配额 / 用量百分比)
     *
     * @param id 客户端编号
     * @return 流量信息
     */
    @GetMapping("/traffic")
    public Result<XrayClientTrafficRespVO> getXrayClientTraffic(@RequestParam("id") String id) {
        XrayClientTrafficRespVO traffic = xrayClientTrafficService.getXrayClientTraffic(id);
        return Result.ok(traffic);
    }

    /**
     * 清零客户端累计流量
     *
     * @param id 客户端编号
     * @return 是否成功
     */
    @PostMapping("/reset-traffic")
    public Result<Boolean> resetXrayClientTraffic(@RequestParam("id") String id) {
        xrayClientTrafficService.resetXrayClientTraffic(id);
        return Result.ok(true);
    }

    /**
     * 拉协议级凭据明文 (UUID / password); 列表 / 详情下发的 clientUuid 是 mask 形式
     *
     * @param id 客户端编号
     * @return 协议级凭据
     */
    @GetMapping("/credential")
    public Result<XrayClientCredentialRespVO> getXrayClientCredential(@RequestParam("id") String id) {
        XrayClientCredentialRespVO credential = xrayClientService.getXrayClientCredential(id);
        return Result.ok(credential);
    }

    /**
     * 获得 server 下客户端同步态 (DB ↔ 远端 inbound 差异统计)
     *
     * @param serverId 服务器编号
     * @return 同步态报告
     */
    @GetMapping("/sync-status")
    public Result<XrayClientSyncStatusRespVO> getSyncStatus(@RequestParam("serverId") String serverId) {
        XrayClientSyncStatusRespVO status = xrayClientService.getSyncStatus(serverId);
        return Result.ok(status);
    }

    /**
     * 单客户端补推 (DB → 远端 inbound 重放)
     *
     * @param id 客户端编号
     * @return 是否成功
     */
    @PostMapping("/sync")
    public Result<Boolean> syncXrayClient(@RequestParam("id") String id) {
        xrayClientService.syncXrayClient(id);
        return Result.ok(true);
    }

    /**
     * server 全量重放 (DB 全量客户端 → 远端 inbound; 返回成功 / 失败计数)
     *
     * @param serverId 服务器编号
     * @return 重放报告
     */
    @PostMapping("/replay-server")
    public Result<XrayClientReplayReportRespVO> replayServer(@RequestParam("serverId") String serverId) {
        XrayClientReplayReportRespVO report = xrayClientService.replayServer(serverId);
        return Result.ok(report);
    }

    /** 单条 detail / create / rotate 共用的 enrich 路径; ipAddress / server / xray_config 三套 map 一次性预拉. */
    private XrayClientRespVO convertOne(XrayClientDO entity) {
        List<XrayClientDO> single = Collections.singletonList(entity);
        Set<String> serverIds = XrayClientConvert.collectServerIds(single);
        Map<String, String> ipMap = loadIpAddressMap(XrayClientConvert.collectIpIds(single));
        Map<String, ResourceServerDO> serverMap = loadServerMap(serverIds);
        Map<String, String> hostMap = loadHostMap(serverIds);
        Map<String, XrayConfigDO> configMap = loadConfigMap(serverIds);
        return XrayClientConvert.INSTANCE.convert(entity, ipMap, serverMap, hostMap, configMap);
    }

    private Map<String, String> loadHostMap(Set<String> serverIds) {
        return credentialService.getHostMap(serverIds);
    }

    private Map<String, String> loadIpAddressMap(Set<String> ipIds) {
        if (ipIds.isEmpty()) return Collections.emptyMap();
        return resourceIpPoolService.getIpAddressMap(ipIds);
    }

    private Map<String, ResourceServerDO> loadServerMap(Set<String> serverIds) {
        if (serverIds.isEmpty()) return Collections.emptyMap();
        return resourceServerService.getServerMap(serverIds);
    }

    private Map<String, XrayConfigDO> loadConfigMap(Set<String> serverIds) {
        if (serverIds.isEmpty()) return Collections.emptyMap();
        return xrayConfigService.listByServerIds(serverIds);
    }
}
