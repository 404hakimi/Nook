package com.nook.biz.node.api.resource;

import com.nook.biz.node.api.resource.dto.ResourceServerRuntimeRespDTO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * 资源服务器运行时 Api 接口
 *
 * @author nook
 */
public interface ResourceServerRuntimeApi {

    /**
     * 批量取服务器运行时
     *
     * @param serverIds 服务器ID集合
     * @return 服务器ID → 运行时 DTO (缺失的不在 map 内)
     */
    Map<String, ResourceServerRuntimeRespDTO> listByServerIds(Collection<String> serverIds);

    /**
     * 记录服务器心跳: 刷新最近心跳时刻、agent 版本与来源 IP, 并清零连续失联计数
     *
     * @param serverId     服务器ID
     * @param at           心跳时刻
     * @param agentVersion agent 自报版本; 空表示不更新
     * @param clientIp     直连来源 IP
     * @return 受影响行数; 0 表示运行时行尚未建立, 调用方需告警
     */
    int onHeartbeat(String serverId, LocalDateTime at, String agentVersion, String clientIp);
}
