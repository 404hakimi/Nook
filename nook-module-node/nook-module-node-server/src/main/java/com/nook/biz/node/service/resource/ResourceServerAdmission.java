package com.nook.biz.node.service.resource;

import cn.hutool.core.collection.CollUtil;
import com.nook.biz.agent.api.enums.AgentOnlineState;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerRuntimeMapper;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 线路机 / 落地机可分配性准入 (一处综合 生命周期 + 流量配额 + 心跳 三类信号)
 *
 * @author nook
 */
@Component
public class ResourceServerAdmission {

    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private ResourceServerCapacityMapper resourceServerCapacityMapper;
    @Resource
    private ResourceServerRuntimeMapper resourceServerRuntimeMapper;

    /**
     * 从候选 server 里筛出健康可分配的子集; 排除 非 LIVE / 配额到顶 / 心跳不健康 (TEMP_UNHEALTHY / OFFLINE / 从未上报).
     *
     * @param serverIds 候选 server 编号
     * @return 可分配的 server 编号子集
     */
    public Set<String> filterAllocatable(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) {
            return Set.of();
        }
        Map<String, ResourceServerDO> serverMap = CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(serverIds), ResourceServerDO::getId);
        Map<String, ResourceServerCapacityDO> capMap = CollectionUtils.convertMap(
                resourceServerCapacityMapper.selectBatchIds(serverIds), ResourceServerCapacityDO::getServerId);
        Map<String, ResourceServerRuntimeDO> rtMap = CollectionUtils.convertMap(
                resourceServerRuntimeMapper.selectBatchIds(serverIds), ResourceServerRuntimeDO::getServerId);
        LocalDateTime now = LocalDateTime.now();
        Set<String> allocatable = new HashSet<>(serverIds.size());
        for (String id : serverIds) {
            if (this.isAllocatable(serverMap.get(id), capMap.get(id), rtMap.get(id), now)) {
                allocatable.add(id);
            }
        }
        return allocatable;
    }

    /** 单台准入判定: LIVE + 未到顶(throttle≠THROTTLED) + 心跳健康(ONLINE / WARN). */
    public boolean isAllocatable(ResourceServerDO srv, ResourceServerCapacityDO cap,
                                 ResourceServerRuntimeDO rt, LocalDateTime now) {
        if (srv == null || !ResourceServerLifecycleEnum.LIVE.matches(srv.getLifecycleState())) {
            return false;
        }
        if (cap != null && ResourceServerThrottleStateEnum.THROTTLED.matches(cap.getThrottleState())) {
            return false;
        }
        Long elapsedSec = (rt == null || rt.getLastHeartbeatAt() == null) ? null
                : Duration.between(rt.getLastHeartbeatAt(), now).getSeconds();
        AgentOnlineState state = AgentOnlineState.classify(elapsedSec);
        return state == AgentOnlineState.ONLINE || state == AgentOnlineState.WARN;
    }

    /**
     * 查 LIVE 线路机里需要故障切换的: 到顶(THROTTLED) 或 掉线(OFFLINE); 返 serverId → 原因.
     * (TEMP_UNHEALTHY 是 3-5min 抖动, 不触发迁移, 只暂停分新.)
     *
     * @return serverId → 原因 (THROTTLED / OFFLINE)
     */
    public Map<String, String> findFrontlinesNeedingFailover() {
        List<ResourceServerDO> frontlines = resourceServerMapper.selectLiveFrontlines();
        if (CollUtil.isEmpty(frontlines)) {
            return Map.of();
        }
        Set<String> ids = CollectionUtils.convertSet(frontlines, ResourceServerDO::getId);
        Map<String, ResourceServerCapacityDO> capMap = CollectionUtils.convertMap(
                resourceServerCapacityMapper.selectBatchIds(ids), ResourceServerCapacityDO::getServerId);
        Map<String, ResourceServerRuntimeDO> rtMap = CollectionUtils.convertMap(
                resourceServerRuntimeMapper.selectBatchIds(ids), ResourceServerRuntimeDO::getServerId);
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> result = new HashMap<>();
        for (ResourceServerDO srv : frontlines) {
            ResourceServerCapacityDO cap = capMap.get(srv.getId());
            if (cap != null && ResourceServerThrottleStateEnum.THROTTLED.matches(cap.getThrottleState())) {
                result.put(srv.getId(), ResourceServerThrottleStateEnum.THROTTLED.getState());
                continue;
            }
            ResourceServerRuntimeDO rt = rtMap.get(srv.getId());
            Long elapsedSec = (rt == null || rt.getLastHeartbeatAt() == null) ? null
                    : Duration.between(rt.getLastHeartbeatAt(), now).getSeconds();
            if (AgentOnlineState.classify(elapsedSec) == AgentOnlineState.OFFLINE) {
                result.put(srv.getId(), AgentOnlineState.OFFLINE.name());
            }
        }
        return result;
    }
}
