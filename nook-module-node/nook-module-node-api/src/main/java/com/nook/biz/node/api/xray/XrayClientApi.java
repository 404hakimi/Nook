package com.nook.biz.node.api.xray;

import com.nook.biz.node.api.xray.dto.XrayClientNodeDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Xray 客户端节点连接信息查询契约.
 *
 * @author nook
 */
public interface XrayClientApi {

    /**
     * 批量查客户端节点连接信息
     *
     * <p>仅返回运行中、且能拼出地址的客户端; 不存在 / 未运行 / 缺配置的客户端跳过, 不抛异常.
     *
     * @param clientIds 客户端ID集合
     * @return 节点连接信息列表 (顺序不保证)
     */
    List<XrayClientNodeDTO> getNodeInfos(Collection<String> clientIds);

    /**
     * 批量查客户端所在线路机
     *
     * @param clientIds 客户端ID集合
     * @return 客户端ID → 线路机ID (缺失的跳过)
     */
    Map<String, String> getServerIdByClientIds(Collection<String> clientIds);

    /**
     * 批量查客户端所在落地机
     *
     * @param clientIds 客户端ID集合
     * @return 客户端ID → 落地机ID (缺失的跳过)
     */
    Map<String, String> getLandingIdByClientIds(Collection<String> clientIds);

    /**
     * 查落地机当前绑定的客户端 (落地机与客户端 1:1)
     *
     * @param landingServerId 落地机ID
     * @return 绑定的客户端ID; 无绑定返 null
     */
    String getClientIdByLandingId(String landingServerId);

    /**
     * 查指定线路机集合上的客户端及其所在线路机
     *
     * @param serverIds 线路机ID集合
     * @return 客户端ID → 线路机ID
     */
    Map<String, String> getClientServerMapByServerIds(Collection<String> serverIds);
}
