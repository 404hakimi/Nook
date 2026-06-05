package com.nook.biz.trade.api;

import com.nook.biz.trade.api.dto.SubscriptionCertRespDTO;

import java.util.Collection;
import java.util.List;

/**
 * 订阅凭证跨模块契约 (node 消费: 对账读凭证、分配回写)
 *
 * @author nook
 */
public interface SubscriptionCertApi {

    /**
     * 查某线路机上应运行的凭证
     *
     * @param serverId 线路机ID
     * @return 凭证列表
     */
    List<SubscriptionCertRespDTO> listActiveByServer(String serverId);

    /**
     * 按ID查凭证
     *
     * @param certId 凭证ID
     * @return 凭证; 不存在返 null
     */
    SubscriptionCertRespDTO getById(String certId);

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
}
