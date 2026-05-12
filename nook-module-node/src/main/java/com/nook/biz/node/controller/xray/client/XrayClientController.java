package com.nook.biz.node.controller.xray.client;

import com.nook.biz.node.controller.xray.client.vo.ClientCredentialRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientPageReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientProvisionReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientTrafficRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientUpdateReqVO;
import com.nook.biz.node.controller.xray.client.vo.ReplayReportRespVO;
import com.nook.biz.node.controller.xray.client.vo.SyncStatusRespVO;
import com.nook.biz.node.convert.xray.XrayClientConvert;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.service.resource.ResourceIpPoolService;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.xray.client.XrayClientService;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理后台 - Xray Client
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/node/xray/client")
@Validated
public class XrayClientController {

    @Resource
    private XrayClientService xrayClientService;
    @Resource
    private ResourceIpPoolService resourceIpPoolService;
    @Resource
    private ResourceServerService resourceServerService;

    @GetMapping
    public Result<PageResult<ClientRespVO>> getXrayClientPage(@ModelAttribute ClientPageReqVO pageReqVO) {
        PageResult<XrayClientDO> pageResult = xrayClientService.getXrayClientPage(pageReqVO);
        Map<String, String> ipMap = loadIpAddressMap(XrayClientConvert.collectIpIds(pageResult.getRecords()));
        Map<String, ResourceServerDO> serverMap = loadServerMap(XrayClientConvert.collectServerIds(pageResult.getRecords()));
        return Result.ok(XrayClientConvert.INSTANCE.convertPage(pageResult, ipMap, serverMap));
    }

    @GetMapping("/{id}")
    public Result<ClientRespVO> getXrayClient(@PathVariable("id") String id) {
        XrayClientDO entity = xrayClientService.getXrayClient(id);
        return Result.ok(convertOne(entity));
    }

    @PostMapping("/provision")
    public Result<ClientRespVO> provisionXrayClient(@RequestBody @Valid ClientProvisionReqVO createReqVO) {
        XrayClientDO client = xrayClientService.provisionXrayClient(createReqVO);
        return Result.ok(convertOne(client));
    }

    @PutMapping("/{id}")
    public Result<Boolean> updateXrayClient(@PathVariable("id") String id,
                                            @RequestBody @Valid ClientUpdateReqVO updateReqVO) {
        xrayClientService.updateXrayClient(id, updateReqVO);
        return Result.ok(true);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> revokeXrayClient(@PathVariable("id") String id) {
        xrayClientService.revokeXrayClient(id);
        return Result.ok(true);
    }

    @PostMapping("/{id}/rotate")
    public Result<ClientRespVO> rotateXrayClient(@PathVariable("id") String id) {
        XrayClientDO entity = xrayClientService.rotateXrayClient(id);
        return Result.ok(convertOne(entity));
    }

    @GetMapping("/{id}/traffic")
    public Result<ClientTrafficRespVO> getXrayClientTraffic(@PathVariable("id") String id) {
        ClientTrafficRespVO traffic = xrayClientService.getXrayClientTraffic(id);
        return Result.ok(traffic);
    }

    @PostMapping("/{id}/reset-traffic")
    public Result<Boolean> resetXrayClientTraffic(@PathVariable("id") String id) {
        xrayClientService.resetXrayClientTraffic(id);
        return Result.ok(true);
    }

    @GetMapping("/{id}/credential")
    public Result<ClientCredentialRespVO> getXrayClientCredential(@PathVariable("id") String id) {
        ClientCredentialRespVO credential = xrayClientService.getXrayClientCredential(id);
        return Result.ok(credential);
    }

    @GetMapping("/server/{serverId}/sync-status")
    public Result<SyncStatusRespVO> getSyncStatus(@PathVariable("serverId") String serverId) {
        SyncStatusRespVO status = xrayClientService.getSyncStatus(serverId);
        return Result.ok(status);
    }

    @PostMapping("/{id}/sync")
    public Result<Boolean> syncXrayClient(@PathVariable("id") String id) {
        xrayClientService.syncXrayClient(id);
        return Result.ok(true);
    }

    @PostMapping("/server/{serverId}/replay")
    public Result<ReplayReportRespVO> replayServer(@PathVariable("serverId") String serverId) {
        ReplayReportRespVO report = xrayClientService.replayServer(serverId);
        return Result.ok(report);
    }

    /** 单条 detail / provision / rotate 共用的 enrich 路径. */
    private ClientRespVO convertOne(XrayClientDO entity) {
        List<XrayClientDO> single = Collections.singletonList(entity);
        Map<String, String> ipMap = loadIpAddressMap(XrayClientConvert.collectIpIds(single));
        Map<String, ResourceServerDO> serverMap = loadServerMap(XrayClientConvert.collectServerIds(single));
        return XrayClientConvert.INSTANCE.convert(entity, ipMap, serverMap);
    }

    private Map<String, String> loadIpAddressMap(Set<String> ipIds) {
        if (ipIds.isEmpty()) return Collections.emptyMap();
        return resourceIpPoolService.getIpAddressMap(ipIds);
    }

    private Map<String, ResourceServerDO> loadServerMap(Set<String> serverIds) {
        if (serverIds.isEmpty()) return Collections.emptyMap();
        return resourceServerService.getServerMap(serverIds);
    }
}
