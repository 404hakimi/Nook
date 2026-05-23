package com.nook.biz.agent.convert;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.agent.api.enums.AgentOnlineState;
import com.nook.biz.agent.controller.admin.vo.AdminAgentDetailRespVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentListItemRespVO;
import com.nook.biz.agent.dal.dataobject.AgentRuntimeConfigDO;
import com.nook.biz.agent.service.AdminAgentService;
import com.nook.biz.node.api.resource.dto.ResourceServerCapacityRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerCredentialRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRuntimeRespDTO;
import com.nook.biz.node.api.xray.dto.XrayNodeRespDTO;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Admin Agent Convert
 *
 * @author nook
 */
@Mapper
public interface AdminAgentConvert {

    AdminAgentConvert INSTANCE = Mappers.getMapper(AdminAgentConvert.class);

    /** 提取 serverId 集合, 供 Controller 批量查关联. */
    default Set<String> extractServerIds(List<ResourceServerRespDTO> records) {
        return CollectionUtils.convertSet(records, ResourceServerRespDTO::getId);
    }

    /**
     * 列表批量拼装: server + 关联聚合 → 列表项 VO; 按规范"三步走" 由 Controller 调度.
     *
     * @param records server raw 列表
     * @param agg     {@link AdminAgentService#loadListAggregates} 批量加载的关联聚合
     * @param now     当前时刻 (统一传, 同一批 elapsedSec 基准一致)
     * @return 列表项 VO 列表
     */
    default List<AdminAgentListItemRespVO> convertList(List<ResourceServerRespDTO> records,
                                                       AdminAgentService.ListAggregates agg,
                                                       LocalDateTime now) {
        return records.stream()
                .map(s -> toListItem(s,
                        agg.credentialMap().get(s.getId()),
                        agg.runtimeMap().get(s.getId()),
                        agg.capacityMap().get(s.getId()),
                        agg.cfgMap().get(s.getId()),
                        agg.xrayMap().get(s.getId()),
                        now))
                .toList();
    }

    /**
     * 组装 admin 列表项: server + runtime + capacity + config + xray node.
     *
     * @param s    server (必填)
     * @param rt   runtime (null = 从未心跳)
     * @param cap  capacity (null = 从未上报 NIC)
     * @param cfg  agent_runtime_config (null = 从未配置)
     * @param xray xray_node (null = 未装 xray); 卡片显示装机完备度用
     * @param now  当前时刻, 用于算 elapsedSec
     * @return 列表项 VO
     */
    default AdminAgentListItemRespVO toListItem(ResourceServerRespDTO s,
                                                ResourceServerCredentialRespDTO credential,
                                                ResourceServerRuntimeRespDTO rt,
                                                ResourceServerCapacityRespDTO cap,
                                                AgentRuntimeConfigDO cfg,
                                                XrayNodeRespDTO xray,
                                                LocalDateTime now) {
        AdminAgentListItemRespVO vo = new AdminAgentListItemRespVO();
        vo.setServerId(s.getId());
        vo.setServerName(s.getName());
        vo.setHost(credential == null ? null : credential.getHost());
        vo.setRegion(s.getRegion());
        vo.setLifecycleState(s.getLifecycleState());
        Long elapsedSec = null;
        Integer tempUnhealthy = null;
        if (rt != null) {
            vo.setAgentVersion(rt.getAgentVersion());
            vo.setLastHeartbeatAt(rt.getLastHeartbeatAt());
            vo.setTempUnhealthy(rt.getTempUnhealthy());
            tempUnhealthy = rt.getTempUnhealthy();
            if (rt.getLastHeartbeatAt() != null) {
                elapsedSec = Duration.between(rt.getLastHeartbeatAt(), now).getSeconds();
                vo.setElapsedSec(elapsedSec);
            }
        }
        vo.setOnlineState(AgentOnlineState.classify(elapsedSec, tempUnhealthy).name());
        vo.setConfigSyncState(AgentRuntimeConfigConvert.INSTANCE.classifySyncState(cfg).name());
        if (cap != null) {
            vo.setMonthlyTrafficGb(cap.getMonthlyTrafficGb());
            vo.setRxBytes(cap.getRxBytes());
            vo.setTxBytes(cap.getTxBytes());
            vo.setUsedTrafficBytes(cap.getUsedTrafficBytes());
            vo.setThrottleState(cap.getThrottleState());
        }
        if (xray != null) {
            vo.setXrayVersion(xray.getXrayVersion());
        }
        return vo;
    }

    /**
     * 组装 admin 详情: server + runtime; agentToken 只暴露末 8 位.
     *
     * @param s   server (必填)
     * @param rt  runtime (null = 从未心跳)
     * @param now 当前时刻
     * @return 详情 VO
     */
    default AdminAgentDetailRespVO toDetail(ResourceServerRespDTO s,
                                            ResourceServerRuntimeRespDTO rt,
                                            LocalDateTime now) {
        AdminAgentDetailRespVO vo = BeanUtils.toBean(s, AdminAgentDetailRespVO.class);
        vo.setServerId(s.getId());
        vo.setLifecycleState(s.getLifecycleState());
        if (StrUtil.isNotBlank(s.getAgentToken()) && s.getAgentToken().length() >= 8) {
            vo.setAgentTokenSuffix("..." + s.getAgentToken().substring(s.getAgentToken().length() - 8));
        }
        Long elapsedSec = null;
        Integer tempUnhealthy = null;
        if (rt != null) {
            vo.setAgentVersion(rt.getAgentVersion());
            vo.setLastAgentSeenIp(rt.getLastAgentSeenIp());
            vo.setLastHeartbeatAt(rt.getLastHeartbeatAt());
            vo.setTempUnhealthy(rt.getTempUnhealthy());
            vo.setConsecutiveMiss(rt.getConsecutiveMiss());
            tempUnhealthy = rt.getTempUnhealthy();
            if (rt.getLastHeartbeatAt() != null) {
                elapsedSec = Duration.between(rt.getLastHeartbeatAt(), now).getSeconds();
                vo.setElapsedSec(elapsedSec);
            }
        }
        vo.setOnlineState(AgentOnlineState.classify(elapsedSec, tempUnhealthy).name());
        return vo;
    }
}
