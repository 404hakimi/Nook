package com.nook.biz.node.controller.xray.client;

import com.nook.biz.node.controller.xray.client.vo.ClientCredentialRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientPageReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientProvisionReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientTrafficRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientUpdateReqVO;
import com.nook.biz.node.controller.xray.client.vo.ReplayReportRespVO;
import com.nook.biz.node.controller.xray.client.vo.SyncStatusRespVO;
import com.nook.biz.node.convert.xray.client.XrayClientConvert;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.resource.dto.ServerBriefDTO;
import com.nook.biz.node.resource.service.ResourceIpPoolService;
import com.nook.biz.node.resource.service.ResourceServerService;
import com.nook.biz.node.service.xray.client.XrayClientService;
import com.nook.common.web.response.PageResult;
import com.nook.common.web.response.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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
 * Xray 客户端后台管理接口
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/node/xray/client")
public class XrayClientController {

    @Resource
    private XrayClientService xrayClientService;
    @Resource
    private ResourceIpPoolService resourceIpPoolService;
    @Resource
    private ResourceServerService resourceServerService;

    @GetMapping
    public Result<PageResult<ClientRespVO>> page(@ModelAttribute ClientPageReqVO reqVO) {
        PageResult<XrayClientDO> entities = xrayClientService.page(reqVO);
        return Result.ok(XrayClientConvert.INSTANCE.convertPage(entities,
                loadIpAddressMap(XrayClientConvert.collectIpIds(entities.getRecords())),
                loadServerBriefMap(XrayClientConvert.collectServerIds(entities.getRecords()))));
    }

    @GetMapping("/{id}")
    public Result<ClientRespVO> detail(@PathVariable String id) {
        XrayClientDO entity = xrayClientService.findById(id);
        return Result.ok(convertOne(entity));
    }

    @PostMapping("/provision")
    public Result<ClientRespVO> provision(@RequestBody @Valid ClientProvisionReqVO reqVO) {
        XrayClientDO client = xrayClientService.provision(reqVO);
        return Result.ok(convertOne(client));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id,
                               @RequestBody @Valid ClientUpdateReqVO reqVO) {
        xrayClientService.update(id, reqVO);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> revoke(@PathVariable String id) {
        xrayClientService.revoke(id);
        return Result.ok();
    }

    @PostMapping("/{id}/rotate")
    public Result<ClientRespVO> rotate(@PathVariable String id) {
        XrayClientDO entity = xrayClientService.rotate(id);
        return Result.ok(convertOne(entity));
    }

    @GetMapping("/{id}/traffic")
    public Result<ClientTrafficRespVO> traffic(@PathVariable String id) {
        return Result.ok(xrayClientService.getTraffic(id));
    }

    @PostMapping("/{id}/reset-traffic")
    public Result<Void> resetTraffic(@PathVariable String id) {
        xrayClientService.resetTraffic(id);
        return Result.ok();
    }

    @GetMapping("/{id}/credential")
    public Result<ClientCredentialRespVO> credential(@PathVariable String id) {
        return Result.ok(xrayClientService.loadCredential(id));
    }

    @GetMapping("/server/{serverId}/sync-status")
    public Result<SyncStatusRespVO> syncStatus(@PathVariable String serverId) {
        return Result.ok(xrayClientService.getSyncStatus(serverId));
    }

    @PostMapping("/{id}/sync")
    public Result<Void> sync(@PathVariable String id) {
        xrayClientService.syncOne(id);
        return Result.ok();
    }

    @PostMapping("/server/{serverId}/replay")
    public Result<ReplayReportRespVO> replay(@PathVariable String serverId) {
        return Result.ok(xrayClientService.replayServer(serverId));
    }

    /** 单条 detail / provision / rotate 共用的 enrich 路径. */
    private ClientRespVO convertOne(XrayClientDO entity) {
        List<XrayClientDO> single = Collections.singletonList(entity);
        return XrayClientConvert.INSTANCE.convert(entity,
                loadIpAddressMap(XrayClientConvert.collectIpIds(single)),
                loadServerBriefMap(XrayClientConvert.collectServerIds(single)));
    }

    private Map<String, String> loadIpAddressMap(Set<String> ipIds) {
        if (ipIds.isEmpty()) return Collections.emptyMap();
        return resourceIpPoolService.loadIpAddressMap(ipIds);
    }

    private Map<String, ServerBriefDTO> loadServerBriefMap(Set<String> serverIds) {
        if (serverIds.isEmpty()) return Collections.emptyMap();
        return resourceServerService.loadBriefMap(serverIds);
    }
}
