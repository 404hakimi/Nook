package com.nook.biz.node.service.xray.client;

import com.nook.biz.node.controller.xray.client.vo.ClientCredentialRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientPageReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientProvisionReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientTrafficRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientUpdateReqVO;
import com.nook.biz.node.controller.xray.client.vo.ReplayReportRespVO;
import com.nook.biz.node.controller.xray.client.vo.SyncStatusRespVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.common.web.response.PageResult;

/**
 * Xray client 全生命周期 (开通 / 吊销 / 轮换 / 查流量); 远端走 SSH + xray CLI.
 *
 * @author nook
 */
public interface XrayClientService {

    /**
     * 单条 client 详情.
     *
     * @param id xray_client.id
     * @return XrayClientDO
     */
    XrayClientDO findById(String id);

    /**
     * 分页查询 client.
     *
     * @param reqVO 分页 + 过滤条件
     * @return PageResult of XrayClientDO
     */
    PageResult<XrayClientDO> page(ClientPageReqVO reqVO);

    /**
     * 开通 client, 远端 addUser 成功后才落 DB; 同 (memberUserId, ipId) 已存在抛 CLIENT_DUPLICATE.
     *
     * @param reqVO 开通入参
     * @return XrayClientDO
     */
    XrayClientDO provision(ClientProvisionReqVO reqVO);

    /**
     * 吊销 client, 远端先删再硬删 DB;
     *
     * @param inboundEntityId xray_client.id
     */
    void revoke(String inboundEntityId);

    /**
     * 轮换协议密钥 (del 旧 → add 新 → update DB), 中途失败标 status=3 待 reconciler 修复.
     *
     * @param inboundEntityId xray_client.id
     * @return XrayClientDO
     */
    XrayClientDO rotate(String inboundEntityId);

    /**
     * 实时流量与配额, 内部读 framework stats 后 convert 成 VO.
     *
     * @param inboundEntityId xray_client.id
     * @return ClientTrafficRespVO
     */
    ClientTrafficRespVO getTraffic(String inboundEntityId);

    /**
     * 累计上下行计数清零, 不影响 client 本身.
     *
     * @param inboundEntityId xray_client.id
     */
    void resetTraffic(String inboundEntityId);

    /**
     * 编辑本地元数据 (listenIp / listenPort / transport / status), 不触达远端.
     *
     * @param inboundEntityId xray_client.id
     * @param reqVO           更新入参
     */
    void update(String inboundEntityId, ClientUpdateReqVO reqVO);

    /**
     * 协议级凭据明文 (UUID + 服务器 host), 拼订阅链接用; 与 list/detail 的 mask 行为区分.
     *
     * @param inboundEntityId xray_client.id
     * @return ClientCredentialRespVO
     */
    ClientCredentialRespVO loadCredential(String inboundEntityId);

    /**
     * server 远端 vs DB 对账; 返回 ok / 缺失 / 孤儿 三类 tag.
     *
     * @param serverId resource_server.id
     * @return SyncStatusRespVO
     */
    SyncStatusRespVO getSyncStatus(String serverId);

    /**
     * 把单条 client 推到远端 (幂等: 远端有就先删再加); 失败标 status=3 抛错.
     *
     * @param clientId xray_client.id
     */
    void syncOne(String clientId);

    /**
     * 把 server 下所有 status≠2 的 client 全推一遍; 失败行收集到报告, 单条失败不阻断整批.
     *
     * @param serverId resource_server.id
     * @return ReplayReportRespVO
     */
    ReplayReportRespVO replayServer(String serverId);

    /**
     * Reconciler 调度入口: 探 xray uptime, 检测到重启 (uptime 比 last_xray_uptime 新) 才 replay 该 server.
     * 平时仅 1 次 SSH 探活, 重启后才 ADI/RMI; 不可达时静默跳过.
     *
     * @param serverId resource_server.id
     */
    void replayIfRestarted(String serverId);
}
