package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.XrayClientNodeDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Xray 客户端节点连接信息查询契约 (trade 拼订阅 URL 用).
 *
 * @author nook
 */
public interface XrayClientNodeApi {

    /**
     * 批量查客户端节点连接信息. 只返存在且 RUNNING、且能拼出 host 的客户端;
     * 不存在 / 非运行 / 缺 inbound 配置 / 无 host 的 clientId 直接跳过 (不抛异常).
     *
     * @param clientIds xray_client.id 集合
     * @return 节点连接信息列表 (顺序不保证, 用 clientId 映射回订阅)
     */
    List<XrayClientNodeDTO> getNodeInfos(Collection<String> clientIds);

    /**
     * 批量查客户端所在线路机 (server_id); trade allocator 算线路机已挂带宽用.
     *
     * @param clientIds xray_client.id 集合
     * @return clientId → server_id (缺失/无 server 的跳过)
     */
    Map<String, String> getServerIdByClientIds(Collection<String> clientIds);
}
