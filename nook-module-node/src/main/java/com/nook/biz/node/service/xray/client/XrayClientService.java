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

import java.util.Collection;
import java.util.Map;

/**
 * Xray Client Service 接口
 *
 * <p>负责 Xray client 全生命周期 (开通 / 吊销 / 轮换 / 查流量); 远端走 SSH + xray CLI.
 *
 * @author nook
 */
public interface XrayClientService {

    /**
     * 获得 Xray Client; 不存在抛 CLIENT_NOT_FOUND
     *
     * @param id xray_client.id
     * @return Xray Client
     */
    XrayClientDO getXrayClient(String id);

    /**
     * 分页查询 Xray Client
     *
     * @param pageReqVO 分页 + 过滤条件
     * @return 分页结果
     */
    PageResult<XrayClientDO> getXrayClientPage(ClientPageReqVO pageReqVO);

    /**
     * 开通 Xray Client; 远端 addUser 成功后才落 DB; 同 (memberUserId, ipId) 已存在抛 CLIENT_DUPLICATE
     *
     * @param createReqVO 开通入参
     * @return 新开通的 Client
     */
    XrayClientDO provisionXrayClient(ClientProvisionReqVO createReqVO);

    /**
     * 吊销 Xray Client; 远端先删再硬删 DB
     *
     * @param id xray_client.id
     */
    void revokeXrayClient(String id);

    /**
     * 轮换协议密钥 (del 旧 → add 新 → update DB), 中途失败标 status=3 待 reconciler 修复
     *
     * @param id xray_client.id
     * @return 轮换后的 Client
     */
    XrayClientDO rotateXrayClient(String id);

    /**
     * 获得 Xray Client 实时流量与配额, 内部读 framework stats 后 convert 成 VO
     *
     * @param id xray_client.id
     * @return 流量信息
     */
    ClientTrafficRespVO getXrayClientTraffic(String id);

    /**
     * 累计上下行计数清零, 不影响 client 本身
     *
     * @param id xray_client.id
     */
    void resetXrayClientTraffic(String id);

    /**
     * 编辑本地元数据 (listenIp / listenPort / transport / status), 不触达远端
     *
     * @param id          xray_client.id
     * @param updateReqVO 更新入参
     */
    void updateXrayClient(String id, ClientUpdateReqVO updateReqVO);

    /**
     * 获得 Xray Client 协议级凭据明文 (UUID + 服务器 host), 拼订阅链接用; 与 list/detail 的 mask 行为区分
     *
     * @param id xray_client.id
     * @return 凭据信息
     */
    ClientCredentialRespVO getXrayClientCredential(String id);

    /**
     * 获得 server 远端 vs DB 对账结果; 返回 ok / 缺失 / 孤儿 三类 tag
     *
     * @param serverId resource_server.id
     * @return 对账结果
     */
    SyncStatusRespVO getSyncStatus(String serverId);

    /**
     * 把单条 Xray Client 推到远端 (幂等: 远端有就先删再加); 失败标 status=3 抛错
     *
     * @param id xray_client.id
     */
    void syncXrayClient(String id);

    /**
     * 把 server 下所有 status≠2 的 client 全推一遍; 失败行收集到报告, 单条失败不阻断整批
     *
     * @param serverId resource_server.id
     * @return Replay 报告
     */
    ReplayReportRespVO replayServer(String serverId);

    /**
     * Reconciler 调度入口: 探 xray uptime, 检测到重启 (uptime 比 last_xray_uptime 新) 才 replay 该 server
     *
     * <p>平时仅 1 次 SSH 探活, 重启后才 ADI/RMI; 不可达时静默跳过.
     *
     * @param serverId resource_server.id
     */
    void replayIfRestarted(String serverId);

    /**
     * 批量取 clientEmail (list enrich 用); 走 selectBatchIds 避免 N+1; 已物理删的 client 不进结果 map
     *
     * @param clientIds id 集合; null / 空返空 map
     * @return Map of clientId → clientEmail
     */
    Map<String, String> getEmailMap(Collection<String> clientIds);
}
