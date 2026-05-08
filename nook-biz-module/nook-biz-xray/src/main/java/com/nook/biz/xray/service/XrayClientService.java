package com.nook.biz.xray.service;

import com.nook.biz.xray.backend.dto.XrayClientTraffic;
import com.nook.biz.xray.backend.dto.XrayInboundInfo;
import com.nook.biz.xray.controller.client.vo.XrayClientCredentialRespVO;
import com.nook.biz.xray.controller.client.vo.XrayClientPageReqVO;
import com.nook.biz.xray.controller.client.vo.XrayClientProvisionReqVO;
import com.nook.biz.xray.controller.client.vo.XrayClientUpdateReqVO;
import com.nook.biz.xray.entity.XrayClient;
import com.nook.common.web.response.PageResult;

import java.util.List;

/**
 * 客户端 (XrayClient) 全生命周期：开通/吊销/轮换/查流量。
 * 所有业务方法外部协议(3x-ui HTTP / Xray gRPC)均通过 backend 抽象调用。
 *
 * 开通和吊销之间的边界 case：
 *   - 开通失败(backend 抛错)不会写 DB，调用方收到异常即代表"没有任何副作用"；
 *   - 吊销时 backend 已找不到 client(CLIENT_NOT_FOUND)被视为成功，DB 同步软删；
 *   - 轮换是 del→add 两步，中间失败需 reconciler 介入恢复，service 不强保证原子。
 */
public interface XrayClientService {

    XrayClient findById(String id);

    PageResult<XrayClient> page(XrayClientPageReqVO reqVO);

    /** 拉远端 inbound 列表(给运营在 IP-inbound 关联界面下拉选)。 */
    List<XrayInboundInfo> listRemoteInbounds(String serverId);

    /** 探活；返回毫秒耗时；失败抛 BusinessException。 */
    long verifyConnectivity(String serverId);

    /**
     * 开通：调 backend.addClient + 落 xray_inbound 行。
     * 同 (memberUserId, ipId) 已存在时抛 CLIENT_DUPLICATE。
     */
    XrayClient provision(XrayClientProvisionReqVO reqVO);

    /** 吊销：backend 删 client + 软删 DB。backend 报 CLIENT_NOT_FOUND 也算成功。 */
    void revoke(String inboundEntityId);

    /** 轮换密钥：del 旧 + add 新 + update DB。 */
    XrayClient rotate(String inboundEntityId);

    /** 拉单条 client 的流量与配额状态。 */
    XrayClientTraffic getTraffic(String inboundEntityId);

    /** 流量计数清零；不动客户端本身。 */
    void resetTraffic(String inboundEntityId);

    /**
     * 修改 inbound 元数据(本地字段)；不触达远端 backend。
     * 只覆盖 listenIp / listenPort / transport / status 四个字段；其它字段动不了。
     */
    XrayClient update(String inboundEntityId, XrayClientUpdateReqVO reqVO);

    /**
     * 拉单条 client 的协议级凭据 (明文 UUID + 服务器 host); 用于"分享给会员"等需要拼订阅链接的场景。
     * 列表 / 详情接口的 RespVO mask UUID, 该接口是 reveal 通道, 调用方明确知晓敏感性。
     */
    XrayClientCredentialRespVO loadCredential(String inboundEntityId);
}
