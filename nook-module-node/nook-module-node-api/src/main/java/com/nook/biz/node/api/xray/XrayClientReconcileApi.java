package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.XrayReconcileClientDTO;

import java.util.List;

/**
 * Xray 客户端期望态查询契约 (agent reconcile 拉取该线路机应存在的全部客户端).
 *
 * @author nook
 */
public interface XrayClientReconcileApi {

    /**
     * 某线路机应存在的全部 xray 客户端期望态 (status=RUNNING), 每条带预拼的 adu/ado/adrules JSON.
     *
     * @param serverId 线路机 server id
     * @return 期望态列表 (空表返空 list)
     */
    List<XrayReconcileClientDTO> getDesiredClients(String serverId);

    /**
     * 某落地机当前应施加的 tc 限速 (Mbps); 落地 1:1, 取占用它的 RUNNING client 的 bandwidthMbps.
     *
     * @param landingServerId 落地机 server id
     * @return 限速 Mbps; 0 = 不限 (无 RUNNING client 占用 / 套餐不限速)
     */
    int getLandingDesiredBandwidthMbps(String landingServerId);
}
