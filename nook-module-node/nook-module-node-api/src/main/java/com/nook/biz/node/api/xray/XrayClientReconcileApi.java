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
}
