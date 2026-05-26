package com.nook.biz.node.service.xray.client;

import com.nook.biz.node.controller.xray.vo.XrayClientCredentialRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientPageReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientProvisionReqVO;
import com.nook.biz.node.controller.xray.vo.XrayClientReplayReportRespVO;
import com.nook.biz.node.controller.xray.vo.XrayClientSyncStatusRespVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.common.web.response.PageResult;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Xray 客户端 Service 接口
 *
 * @author nook
 */
public interface XrayClientService {

    /**
     * 获得 xray 客户端
     *
     * @param id 客户端编号
     * @return xray 客户端
     */
    XrayClientDO getXrayClient(String id);

    /**
     * 获得 xray 客户端分页
     *
     * @param pageReqVO 分页条件
     * @return 客户端分页
     */
    PageResult<XrayClientDO> getXrayClientPage(XrayClientPageReqVO pageReqVO);

    /**
     * 开通 xray 客户端
     *
     * @param createReqVO 开通入参
     * @return 新开通的客户端
     */
    XrayClientDO provisionXrayClient(XrayClientProvisionReqVO createReqVO);

    /**
     * 吊销 xray 客户端
     *
     * @param id 客户端编号
     */
    void revokeXrayClient(String id);

    /**
     * 轮换协议密钥
     *
     * @param id 客户端编号
     * @return 轮换后的客户端
     */
    XrayClientDO rotateXrayClient(String id);

    /**
     * 获得协议级凭据明文
     *
     * @param id 客户端编号
     * @return 协议级凭据
     */
    XrayClientCredentialRespVO getXrayClientCredential(String id);

    /**
     * 获得 server 远端 vs DB 同步态
     *
     * @param serverId 服务器编号
     * @return 同步态
     */
    XrayClientSyncStatusRespVO getSyncStatus(String serverId);

    /**
     * 单客户端补推到远端
     *
     * @param id 客户端编号
     */
    void syncXrayClient(String id);

    /**
     * server 下客户端全量重放
     *
     * @param serverId 服务器编号
     * @return 重放报告
     */
    XrayClientReplayReportRespVO replayServer(String serverId);

    /**
     * 批量获得客户端 email
     *
     * @param clientIds 客户端编号集合
     * @return 客户端编号 → email
     */
    Map<String, String> getEmailMap(Collection<String> clientIds);

    /**
     * 批量获得客户端 DO
     *
     * @param clientIds 客户端编号集合
     * @return 客户端编号 → 客户端 DO
     */
    Map<String, XrayClientDO> getXrayClientMap(Collection<String> clientIds);

    /**
     * 批量预拉 enrich 所需的 4 张子表/主表 map
     *
     * @param serverIds 线路机 server id 集合
     * @param ipIds     落地 server id 集合
     * @return 4 张 map 的批量返回包
     */
    EnrichBundle loadEnrichBundle(Set<String> serverIds, Set<String> ipIds);

    /** Enrich 用 4 张 map 的批量返回包. */
    record EnrichBundle(
            Map<String, String> ipMap,
            Map<String, ResourceServerDO> serverMap,
            Map<String, String> hostMap,
            Map<String, XrayConfigDO> configMap) { }
}
