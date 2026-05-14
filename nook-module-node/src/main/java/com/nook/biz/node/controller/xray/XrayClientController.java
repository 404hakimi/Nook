package com.nook.biz.node.controller.xray;

import com.nook.biz.node.controller.xray.vo.XrayClientCredentialRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientPageReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientProvisionReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientTrafficRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientUpdateReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientReplayReportRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientSyncStatusRespVO;
import com.nook.biz.node.convert.xray.XrayClientConvert;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.service.resource.ResourceIpPoolService;
import com.nook.biz.node.service.resource.ResourceServerService;
import com.nook.biz.node.service.xray.client.XrayClientService;
import com.nook.biz.node.service.xray.client.XrayClientTrafficService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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
@RequestMapping("/admin/xray/client")
@Validated
public class XrayClientController {

    @Resource
    private XrayClientService xrayClientService;
    @Resource
    private XrayClientTrafficService xrayClientTrafficService;
    @Resource
    private ResourceIpPoolService resourceIpPoolService;
    @Resource
    private ResourceServerService resourceServerService;

    @GetMapping("/page")
    public Result<PageResult<XrayClientRespVO>> getXrayClientPage(@ModelAttribute XrayClientPageReqVO pageReqVO) {
        PageResult<XrayClientDO> pageResult = xrayClientService.getXrayClientPage(pageReqVO);
        Map<String, String> ipMap = loadIpAddressMap(XrayClientConvert.collectIpIds(pageResult.getRecords()));
        Map<String, ResourceServerDO> serverMap = loadServerMap(XrayClientConvert.collectServerIds(pageResult.getRecords()));
        return Result.ok(XrayClientConvert.INSTANCE.convertPage(pageResult, ipMap, serverMap));
    }

    @GetMapping("/get")
    public Result<XrayClientRespVO> getXrayClient(@RequestParam("id") String id) {
        XrayClientDO entity = xrayClientService.getXrayClient(id);
        return Result.ok(convertOne(entity));
    }

    @PostMapping("/create")
    public Result<XrayClientRespVO> createXrayClient(@Valid @RequestBody XrayClientProvisionReqVO createReqVO) {
        XrayClientDO client = xrayClientService.provisionXrayClient(createReqVO);
        return Result.ok(convertOne(client));
    }

    @PutMapping("/update")
    public Result<Boolean> updateXrayClient(@RequestParam("id") String id,
                                            @Valid @RequestBody XrayClientUpdateReqVO updateReqVO) {
        xrayClientService.updateXrayClient(id, updateReqVO);
        return Result.ok(true);
    }

    @DeleteMapping("/delete")
    public Result<Boolean> deleteXrayClient(@RequestParam("id") String id) {
        xrayClientService.revokeXrayClient(id);
        return Result.ok(true);
    }

    @PostMapping("/rotate")
    public Result<XrayClientRespVO> rotateXrayClient(@RequestParam("id") String id) {
        XrayClientDO entity = xrayClientService.rotateXrayClient(id);
        return Result.ok(convertOne(entity));
    }

    @GetMapping("/traffic")
    public Result<XrayClientTrafficRespVO> getXrayClientTraffic(@RequestParam("id") String id) {
        XrayClientTrafficRespVO traffic = xrayClientTrafficService.getXrayClientTraffic(id);
        return Result.ok(traffic);
    }

    @PostMapping("/reset-traffic")
    public Result<Boolean> resetXrayClientTraffic(@RequestParam("id") String id) {
        xrayClientTrafficService.resetXrayClientTraffic(id);
        return Result.ok(true);
    }

    @GetMapping("/credential")
    public Result<XrayClientCredentialRespVO> getXrayClientCredential(@RequestParam("id") String id) {
        XrayClientCredentialRespVO credential = xrayClientService.getXrayClientCredential(id);
        return Result.ok(credential);
    }

    @GetMapping("/sync-status")
    public Result<XrayClientSyncStatusRespVO> getSyncStatus(@RequestParam("serverId") String serverId) {
        XrayClientSyncStatusRespVO status = xrayClientService.getSyncStatus(serverId);
        return Result.ok(status);
    }

    @PostMapping("/sync")
    public Result<Boolean> syncXrayClient(@RequestParam("id") String id) {
        xrayClientService.syncXrayClient(id);
        return Result.ok(true);
    }

    @PostMapping("/replay-server")
    public Result<XrayClientReplayReportRespVO> replayServer(@RequestParam("serverId") String serverId) {
        XrayClientReplayReportRespVO report = xrayClientService.replayServer(serverId);
        return Result.ok(report);
    }

    /** 单条 detail / create / rotate 共用的 enrich 路径 */
    private XrayClientRespVO convertOne(XrayClientDO entity) {
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
