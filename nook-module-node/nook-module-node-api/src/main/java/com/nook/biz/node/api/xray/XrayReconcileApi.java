package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.XrayReconcileClientDTO;

import java.util.List;

/**
 * Xray 期望态对账 Api 接口
 *
 * @author nook
 */
public interface XrayReconcileApi {

    /**
     * 查某线路机应存在的全部客户端期望态
     *
     * @param serverId 线路机ID
     * @return 期望态列表
     */
    List<XrayReconcileClientDTO> getDesiredClients(String serverId);
}
