package com.nook.biz.node.service.resource;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.agent.api.enums.AgentOnlineState;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerThrottleStateEnum;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerTrafficDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerRuntimeMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerTrafficMapper;
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
 * 线路机 / 落地机可分配性准入 (一处综合 生命周期 + 流量限流 + 心跳 三类信号)
 *
 * @author nook
 */
@Component
public class ResourceServerAdmission {

    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private ResourceServerTrafficMapper resourceServerTrafficMapper;
    @Resource
    private ResourceServerRuntimeMapper resourceServerRuntimeMapper;

    /**
     * 从候选服务器中筛出健康可分配的子集
     *
     * <p>排除未上线、流量到顶限流、心跳不健康的服务器.
     *
     * @param serverIds 候选服务器ID集合
     * @return 可分配的服务器ID子集
     */
    public Set<String> filterAllocatable(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) {
            return Set.of();
        }
        Map<String, ResourceServerDO> serverMap = CollectionUtils.convertMap(
                resourceServerMapper.selectBatchIds(serverIds), ResourceServerDO::getId);
        Map<String, ResourceServerTrafficDO> trafficMap = CollectionUtils.convertMap(
                resourceServerTrafficMapper.selectCurrentByServerIds(serverIds), ResourceServerTrafficDO::getServerId);
        Map<String, ResourceServerRuntimeDO> rtMap = CollectionUtils.convertMap(
                resourceServerRuntimeMapper.selectBatchIds(serverIds), ResourceServerRuntimeDO::getServerId);
        LocalDateTime now = LocalDateTime.now();
        Set<String> allocatable = new HashSet<>(serverIds.size());
        for (String id : serverIds) {
            if (this.isAllocatable(serverMap.get(id), trafficMap.get(id), rtMap.get(id), now)) {
                allocatable.add(id);
            }
        }
        return allocatable;
    }

    /** 单台服务器准入判定: 运行中 + 未触发限流 + 心跳健康. */
    public boolean isAllocatable(ResourceServerDO srv, ResourceServerTrafficDO traffic,
                                 ResourceServerRuntimeDO rt, LocalDateTime now) {
        if (ObjectUtil.isNull(srv) || !ResourceServerLifecycleEnum.LIVE.matches(srv.getLifecycleState())) {
            return false;
        }
        if (this.isThrottled(traffic)) {
            return false;
        }
        Long elapsedSec = (ObjectUtil.isNull(rt) || ObjectUtil.isNull(rt.getLastHeartbeatAt())) ? null
                : Duration.between(rt.getLastHeartbeatAt(), now).getSeconds();
        AgentOnlineState state = AgentOnlineState.classify(elapsedSec);
        return state == AgentOnlineState.ONLINE || state == AgentOnlineState.WARN;
    }

    /**
     * 查运行中线路机里需要故障切换的 (已触发限流或掉线)
     *
     * <p>短时心跳抖动不触发迁移, 只暂停分配新客户.
     *
     * @return 服务器ID → 切换原因 (已触发限流 / 掉线)
     */
    public Map<String, String> findFrontlinesNeedingFailover() {
        List<ResourceServerDO> frontlines = resourceServerMapper.selectLiveFrontlines();
        if (CollUtil.isEmpty(frontlines)) {
            return Map.of();
        }
        Set<String> ids = CollectionUtils.convertSet(frontlines, ResourceServerDO::getId);
        Map<String, ResourceServerTrafficDO> trafficMap = CollectionUtils.convertMap(
                resourceServerTrafficMapper.selectCurrentByServerIds(ids), ResourceServerTrafficDO::getServerId);
        Map<String, ResourceServerRuntimeDO> rtMap = CollectionUtils.convertMap(
                resourceServerRuntimeMapper.selectBatchIds(ids), ResourceServerRuntimeDO::getServerId);
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> result = new HashMap<>();
        for (ResourceServerDO srv : frontlines) {
            if (this.isThrottled(trafficMap.get(srv.getId()))) {
                result.put(srv.getId(), ResourceServerThrottleStateEnum.THROTTLED.getState());
                continue;
            }
            ResourceServerRuntimeDO rt = rtMap.get(srv.getId());
            Long elapsedSec = (ObjectUtil.isNull(rt) || ObjectUtil.isNull(rt.getLastHeartbeatAt())) ? null
                    : Duration.between(rt.getLastHeartbeatAt(), now).getSeconds();
            if (AgentOnlineState.classify(elapsedSec) == AgentOnlineState.OFFLINE) {
                result.put(srv.getId(), AgentOnlineState.OFFLINE.name());
            }
        }
        return result;
    }

    /** 当周期测量行已置限流. */
    private boolean isThrottled(ResourceServerTrafficDO traffic) {
        return ObjectUtil.isNotNull(traffic)
                && ResourceServerThrottleStateEnum.THROTTLED.matches(traffic.getThrottleState());
    }
}
