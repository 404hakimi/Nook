package com.nook.biz.trade.service;

import com.nook.biz.trade.dal.dataobject.TradeSubscriptionCertificateDO;

import java.util.Collection;
import java.util.List;

/**
 * 订阅凭证 Service 接口
 *
 * @author nook
 */
public interface TradeSubscriptionCertificateService {

    /**
     * 签发凭证 (生成身份 + 密钥, 期望态置为应运行, 暂不分配)
     *
     * @param subscriptionId 所属订阅
     * @param memberUserId   所属会员 (拼连接身份用)
     * @param source         来源
     * @return 凭证
     */
    TradeSubscriptionCertificateDO issue(String subscriptionId, String memberUserId, String source);

    /**
     * 写入资源分配
     *
     * @param certId   凭证ID
     * @param serverId 线路机ID
     * @param ipId     落地机ID
     */
    void setAllocation(String certId, String serverId, String ipId);

    /**
     * 清空资源分配
     *
     * @param certId 凭证ID
     */
    void clearAllocation(String certId);

    /**
     * 更新期望态
     *
     * @param certId     凭证ID
     * @param certStatus 期望态
     */
    void updateCertStatus(String certId, String certStatus);

    /**
     * 吊销凭证: 置应移除 + 清空分配 (腾出落地机占用记录, 不挡再分配), 一次更新
     *
     * @param certId 凭证ID
     */
    void revoke(String certId);

    /**
     * 获得凭证
     *
     * @param certId 凭证ID
     * @return 凭证; 不存在返 null
     */
    TradeSubscriptionCertificateDO get(String certId);

    /**
     * 查某线路机上应运行的凭证
     *
     * @param serverId 线路机ID
     * @return 凭证列表
     */
    List<TradeSubscriptionCertificateDO> listActiveByServer(String serverId);

    /**
     * 查某订阅的全部凭证
     *
     * @param subscriptionId 订阅ID
     * @return 凭证列表
     */
    List<TradeSubscriptionCertificateDO> listBySubscription(String subscriptionId);

    /**
     * 批量查多订阅的凭证
     *
     * @param subscriptionIds 订阅ID集合
     * @return 凭证列表
     */
    List<TradeSubscriptionCertificateDO> listBySubscriptionIds(Collection<String> subscriptionIds);

    /**
     * 批量查凭证
     *
     * @param certIds 凭证ID集合
     * @return 凭证列表
     */
    List<TradeSubscriptionCertificateDO> listByIds(Collection<String> certIds);

    /**
     * 按落地机查凭证 (落地机与凭证 1:1)
     *
     * @param ipId 落地机ID
     * @return 凭证; 无返 null
     */
    TradeSubscriptionCertificateDO getByIpId(String ipId);
}
