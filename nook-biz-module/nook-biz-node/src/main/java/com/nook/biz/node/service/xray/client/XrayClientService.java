package com.nook.biz.node.service.xray.client;

import com.nook.biz.node.controller.xray.client.vo.ClientCredentialRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientPageReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientProvisionReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientTrafficRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientUpdateReqVO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.common.web.response.PageResult;

/** Client 全生命周期 (开通/吊销/轮换/查流量); 远端通过 ServerSessionManager 统一调度. */
public interface XrayClientService {

    XrayClientDO findById(String id);

    PageResult<XrayClientDO> page(ClientPageReqVO reqVO);

    /** 同 (memberUserId, ipId) 已存在抛 CLIENT_DUPLICATE; 远端 addUser 失败不写 DB. */
    XrayClientDO provision(ClientProvisionReqVO reqVO);

    /** 远端 CLIENT_NOT_FOUND 也算成功; DB 软删 + reconcile 清场 (失败仅 warn). */
    void revoke(String inboundEntityId);

    /** del 旧 + add 新 + update DB; add 失败标 status=3 待 reconciler 修复. */
    XrayClientDO rotate(String inboundEntityId);

    /** 实时流量 + 配额; 直接返回 VO (内部读 framework stats + convert). */
    ClientTrafficRespVO getTraffic(String inboundEntityId);

    /** 流量计数清零; 不动客户端本身. */
    void resetTraffic(String inboundEntityId);

    /** 仅覆盖 listenIp/listenPort/transport/status 本地元数据; 不触达远端. */
    XrayClientDO update(String inboundEntityId, ClientUpdateReqVO reqVO);

    /** 协议级凭据明文 (UUID + 服务器 host); 仅"分享给会员"场景按需取, 列表接口下发的是 mask 形式. */
    ClientCredentialRespVO loadCredential(String inboundEntityId);
}
