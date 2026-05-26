package com.nook.biz.node.controller.xray;

import com.nook.biz.node.controller.xray.vo.XrayClientCredentialRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientPageReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientProvisionReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientReplayReportRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientSyncStatusRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientTrafficRespVO;
import com.nook.biz.node.convert.xray.XrayClientConvert;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.service.xray.client.XrayClientService;
import com.nook.biz.node.service.xray.client.XrayClientTrafficService;
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

    /**
     * 获得 xray 客户端分页
     *
     * @param pageReqVO 分页条件
     * @return 客户端分页
     */
    @GetMapping("/page-xray-client")
    public Result<PageResult<XrayClientRespVO>> getXrayClientPage(@ModelAttribute XrayClientPageReqVO pageReqVO) {
        PageResult<XrayClientDO> pageResult = xrayClientService.getXrayClientPage(pageReqVO);
        XrayClientService.EnrichBundle bundle = xrayClientService.loadEnrichBundle(
                XrayClientConvert.collectServerIds(pageResult.getRecords()),
                XrayClientConvert.collectIpIds(pageResult.getRecords()));
        return Result.ok(XrayClientConvert.INSTANCE.convertPage(pageResult,
                bundle.ipMap(), bundle.serverMap(), bundle.hostMap(), bundle.configMap()));
    }

    /**
     * 获得 xray 客户端详情
     *
     * @param id 客户端编号
     * @return 客户端详情
     */
    @GetMapping("/get-xray-client")
    public Result<XrayClientRespVO> getXrayClient(@RequestParam("id") String id) {
        return Result.ok(convertOne(xrayClientService.getXrayClient(id)));
    }

    /**
     * 开通 xray 客户端
     *
     * @param createReqVO 开通入参
     * @return 客户端详情
     */
    @PostMapping("/create-xray-client")
    public Result<XrayClientRespVO> createXrayClient(@Valid @RequestBody XrayClientProvisionReqVO createReqVO) {
        return Result.ok(convertOne(xrayClientService.provisionXrayClient(createReqVO)));
    }

    /**
     * 吊销 xray 客户端
     *
     * @param id 客户端编号
     * @return 是否成功
     */
    @DeleteMapping("/delete-xray-client")
    public Result<Boolean> deleteXrayClient(@RequestParam("id") String id) {
        xrayClientService.revokeXrayClient(id);
        return Result.ok(true);
    }

    /**
     * 轮换客户端 UUID / 密钥
     *
     * @param id 客户端编号
     * @return 客户端详情
     */
    @PostMapping("/rotate-xray-client")
    public Result<XrayClientRespVO> rotateXrayClient(@RequestParam("id") String id) {
        return Result.ok(convertOne(xrayClientService.rotateXrayClient(id)));
    }

    /**
     * 获得客户端实时流量
     *
     * @param id 客户端编号
     * @return 流量信息
     */
    @GetMapping("/get-xray-client-traffic")
    public Result<XrayClientTrafficRespVO> getXrayClientTraffic(@RequestParam("id") String id) {
        return Result.ok(xrayClientTrafficService.getXrayClientTraffic(id));
    }

    /**
     * 清零客户端累计流量
     *
     * @param id 客户端编号
     * @return 是否成功
     */
    @PostMapping("/reset-xray-client-traffic")
    public Result<Boolean> resetXrayClientTraffic(@RequestParam("id") String id) {
        xrayClientTrafficService.resetXrayClientTraffic(id);
        return Result.ok(true);
    }

    /**
     * 拉协议级凭据明文
     *
     * @param id 客户端编号
     * @return 协议级凭据
     */
    @GetMapping("/get-xray-client-credential")
    public Result<XrayClientCredentialRespVO> getXrayClientCredential(@RequestParam("id") String id) {
        return Result.ok(xrayClientService.getXrayClientCredential(id));
    }

    /**
     * 获得 server 下客户端同步态
     *
     * @param serverId 服务器编号
     * @return 同步态报告
     */
    @GetMapping("/get-sync-status")
    public Result<XrayClientSyncStatusRespVO> getSyncStatus(@RequestParam("serverId") String serverId) {
        return Result.ok(xrayClientService.getSyncStatus(serverId));
    }

    /**
     * 单客户端补推
     *
     * @param id 客户端编号
     * @return 是否成功
     */
    @PostMapping("/sync-xray-client")
    public Result<Boolean> syncXrayClient(@RequestParam("id") String id) {
        xrayClientService.syncXrayClient(id);
        return Result.ok(true);
    }

    /**
     * server 全量重放
     *
     * @param serverId 服务器编号
     * @return 重放报告
     */
    @PostMapping("/replay-xray-server")
    public Result<XrayClientReplayReportRespVO> replayServer(@RequestParam("serverId") String serverId) {
        return Result.ok(xrayClientService.replayServer(serverId));
    }

    /** 单条 detail / create / rotate 共用 enrich 路径. */
    private XrayClientRespVO convertOne(XrayClientDO entity) {
        List<XrayClientDO> single = Collections.singletonList(entity);
        Set<String> serverIds = XrayClientConvert.collectServerIds(single);
        Set<String> ipIds = XrayClientConvert.collectIpIds(single);
        XrayClientService.EnrichBundle bundle = xrayClientService.loadEnrichBundle(serverIds, ipIds);
        return XrayClientConvert.INSTANCE.convert(entity,
                bundle.ipMap(), bundle.serverMap(), bundle.hostMap(), bundle.configMap());
    }
}
