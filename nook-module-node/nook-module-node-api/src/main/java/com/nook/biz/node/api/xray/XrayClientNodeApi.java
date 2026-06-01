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

    /**
     * 批量查客户端所在落地机 (ip_id); trade 按订阅算落地机 tx 流量用 (落地机 1:1).
     *
     * @param clientIds xray_client.id 集合
     * @return clientId → ip_id (落地机 server id; 缺失的跳过)
     */
    Map<String, String> getLandingIdByClientIds(Collection<String> clientIds);

    /**
     * 查落地机当前绑定的客户端 (落地机 1:1); trade 解析落地机限速直查用.
     *
     * @param landingServerId 落地机 server id
     * @return 绑定的 xray_client.id; 无绑定返 null
     */
    String getClientIdByLandingId(String landingServerId);

    /**
     * 查指定线路机集合上的客户端 → 所在线路机; trade 算线路机已挂带宽用 (只捞这批线路机的 client).
     *
     * @param serverIds 线路机 server id 集合
     * @return clientId → server_id
     */
    Map<String, String> getClientServerMapByServerIds(Collection<String> serverIds);
}
