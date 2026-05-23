package com.nook.biz.agent.convert;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.agent.api.enums.AgentOnlineState;
import com.nook.biz.agent.controller.admin.vo.AdminAgentDetailRespVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentListItemRespVO;
import com.nook.biz.agent.dal.dataobject.AgentRuntimeConfigDO;
import com.nook.biz.node.api.resource.dto.ResourceServerCapacityRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerCredentialRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRuntimeRespDTO;
import com.nook.common.utils.object.BeanUtils;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Admin Agent Convert
 *
 * @author nook
 */
@Mapper
public interface AdminAgentConvert {

    AdminAgentConvert INSTANCE = Mappers.getMapper(AdminAgentConvert.class);

    /**
     * 组装 admin 列表项: server + runtime + capacity + config.
     *
     * @param s   server (必填)
     * @param rt  runtime (null = 从未心跳)
     * @param cap capacity (null = 从未上报 NIC)
     * @param cfg agent_runtime_config (null = 从未配置)
     * @param now 当前时刻, 用于算 elapsedSec
     * @return 列表项 VO
     */
    default AdminAgentListItemRespVO toListItem(ResourceServerRespDTO s,
                                                ResourceServerCredentialRespDTO credential,
                                                ResourceServerRuntimeRespDTO rt,
                                                ResourceServerCapacityRespDTO cap,
                                                AgentRuntimeConfigDO cfg,
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
