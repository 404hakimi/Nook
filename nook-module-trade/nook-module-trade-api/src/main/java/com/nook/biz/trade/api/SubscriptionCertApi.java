package com.nook.biz.trade.api;

import com.nook.biz.trade.api.dto.SubscriptionCertRespDTO;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 订阅凭证跨模块契约 (node 消费: 对账读凭证、分配回写)
 *
 * @author nook
 */
public interface SubscriptionCertApi {

    /**
     * 查候选组含该线路机的应运行凭证 (主或备含该机都算; 对账下发、删机守卫、重装告警共用)
     *
     * @param serverId 线路机ID
     * @return 凭证列表
     */
    List<SubscriptionCertRespDTO> listActiveByServerInGroup(String serverId);

    /**
     * 按ID查凭证
     *
     * @param certId 凭证ID
     * @return 凭证; 不存在返 null
     */
    SubscriptionCertRespDTO getById(String certId);

    /**
     * 按落地机查凭证 (落地机与凭证 1:1)
     *
     * @param ipId 落地机ID
     * @return 凭证; 无返 null
     */
    SubscriptionCertRespDTO getByIp(String ipId);

    /**
     * 批量查凭证
     *
     * @param certIds 凭证ID集合
     * @return 凭证列表
     */
    List<SubscriptionCertRespDTO> listByIds(Collection<String> certIds);

    /**
     * 写入资源分配 (占用落地机 / 挑线路机后回写)
     *
     * @param certId   凭证ID
     * @param serverId 线路机ID
     * @param ipId     落地机ID
     */
    void setAllocation(String certId, String serverId, String ipId);

    /**
     * 清空资源分配 (释放落地机时)
     *
     * @param certId 凭证ID
     */
    void clearAllocation(String certId);

    /**
     * 过滤出这批落地机里已被凭证占用的 (ip_id 非空即占用)
     *
     * @param ipIds 落地机ID集合
     * @return 已占用的落地机ID集合
     */
    Set<String> filterBoundIpIds(Collection<String> ipIds);
}
