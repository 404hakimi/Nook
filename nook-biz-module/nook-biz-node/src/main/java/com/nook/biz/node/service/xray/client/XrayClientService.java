package com.nook.biz.node.service.xray.client;

import com.nook.biz.node.controller.xray.client.vo.ClientCredentialRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientPageReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientProvisionReqVO;
import com.nook.biz.node.controller.xray.client.vo.ClientTrafficRespVO;
import com.nook.biz.node.controller.xray.client.vo.ClientUpdateReqVO;
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
     * 吊销 client, 远端先删再软删 DB; 远端 CLIENT_NOT_FOUND 也算成功 (目标态本就是没了).
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
     * @return XrayClientDO
     */
    XrayClientDO update(String inboundEntityId, ClientUpdateReqVO reqVO);

    /**
     * 协议级凭据明文 (UUID + 服务器 host), 拼订阅链接用; 与 list/detail 的 mask 行为区分.
     *
     * @param inboundEntityId xray_client.id
     * @return ClientCredentialRespVO
     */
    ClientCredentialRespVO loadCredential(String inboundEntityId);
}
