package com.nook.biz.node.convert.resource;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingBillingRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingQuotaRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingInstallRespVO;
import com.nook.biz.agent.api.enums.AgentOnlineState;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingListItemRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingSocks5RespVO;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerBillingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerQuotaDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.Socks5InstallDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerTrafficDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * SOCKS5 落地节点 Convert
 *
 * @author nook
 */
@Mapper
public interface ResourceServerLandingConvert {

    ResourceServerLandingConvert INSTANCE = Mappers.getMapper(ResourceServerLandingConvert.class);

    // 主表 + landing 子表 → 跨模块概要 DTO; serverId 取主表 id, landing 为 null 时 status/ipType 留空
    @Mapping(target = "serverId", source = "server.id")
    LandingSummaryDTO toSummary(ResourceServerDO server, Socks5InstallDO landing);

    /** 批量拼概要: serverId 集合 + 主表 / landing 子表 Map → 概要列表 (主表不存在的跳过). */
    default List<LandingSummaryDTO> toSummaries(Collection<String> serverIds,
                                               Map<String, ResourceServerDO> serverMap,
                                               Map<String, Socks5InstallDO> landingMap) {
        List<LandingSummaryDTO> list = new ArrayList<>(serverIds.size());
        for (String serverId : serverIds) {
            ResourceServerDO server = serverMap.get(serverId);
            if (ObjectUtil.isNotNull(server)) {
                list.add(toSummary(server, landingMap.get(serverId)));
            }
        }
        return list;
    }

    /** 主表 → RespVO (仅主表字段; 子表字段需另行回填) */
    ServerLandingRespVO convert(ResourceServerDO bean);

    /** landing 子表 → SOCKS5 配置 VO */
    ServerLandingSocks5RespVO toSocks5RespVO(Socks5InstallDO landing);

    /** landing 子表 → 装机事实 VO */
    ServerLandingInstallRespVO toInstallRespVO(Socks5InstallDO landing);

    /** billing 子表 → 账面 VO */
    ServerLandingBillingRespVO toBillingRespVO(ResourceServerBillingDO bill);

    /** 配额配置 + 当周期测量 → 配额监控 VO. */
    default ServerLandingQuotaRespVO toQuotaRespVO(ResourceServerQuotaDO quota, ResourceServerTrafficDO traffic) {
        ServerLandingQuotaRespVO vo = new ServerLandingQuotaRespVO();
        if (ObjectUtil.isNull(quota)) {
            return vo;
        }
        vo.setServerId(quota.getServerId());
        vo.setBandwidthMbps(quota.getBandwidthMbps());
        vo.setTotalGb(quota.getTotalGb());
        vo.setResetPolicy(quota.getResetPolicy());
        vo.setResetDay(quota.getResetDay());
        if (ObjectUtil.isNotNull(traffic)) {
            vo.setUsedBytes(traffic.getUsedBytes());
            vo.setRxBytes(traffic.getRxBytes());
            vo.setTxBytes(traffic.getTxBytes());
            vo.setThrottleState(traffic.getThrottleState());
        }
        return vo;
    }

    /** 详情聚合: 主表 + 子表 → RespVO (SSH 凭据走公共 /get-credential, 不在此 VO) */
    default ServerLandingRespVO convertWithSubtables(ResourceServerDO main,
                                                    Socks5InstallDO landing,
                                                    ResourceServerBillingDO bill,
                                                    ResourceServerQuotaDO quota,
                                                    ResourceServerTrafficDO traffic,
                                                    ResourceServerRuntimeDO runtime) {
        ServerLandingRespVO vo = convert(main);
        enrichLanding(vo, landing);
        enrichBilling(vo, bill);
        enrichQuota(vo, quota, traffic);
        enrichRuntime(vo, runtime);
        return vo;
    }

    /** 回填落地机列表项 agent 在线态 (连表 SQL 只出原始字段). */
    static void fillOnlineState(ServerLandingListItemRespVO vo, LocalDateTime now) {
        Long elapsedSec = ObjectUtil.isNull(vo.getLastHeartbeatAt()) ? null
                : Duration.between(vo.getLastHeartbeatAt(), now).getSeconds();
        vo.setElapsedSec(elapsedSec);
        vo.setOnlineState(AgentOnlineState.classify(elapsedSec).name());
    }

    static void enrichLanding(ServerLandingRespVO vo, Socks5InstallDO landing) {
        if (ObjectUtil.isNull(vo) || ObjectUtil.isNull(landing)) return;
        vo.setIpTypeId(landing.getIpTypeId());
        vo.setSocks5Port(landing.getSocks5Port());
        vo.setSocks5Username(landing.getSocks5Username());
        vo.setSocks5Password(landing.getSocks5Password());
        vo.setProvisionMode(landing.getProvisionMode());
        vo.setLogLevel(landing.getLogLevel());
        vo.setLogPath(landing.getLogPath());
        vo.setAutostartEnabled(landing.getAutostartEnabled());
        vo.setFirewallEnabled(landing.getFirewallEnabled());
        vo.setInstallDir(landing.getInstallDir());
        vo.setInstalledAt(landing.getInstalledAt());
        vo.setDanteVersion(landing.getDanteVersion());
    }

    static void enrichBilling(ServerLandingRespVO vo, ResourceServerBillingDO bill) {
        if (ObjectUtil.isNull(vo) || ObjectUtil.isNull(bill)) return;
        vo.setCostMonthly(bill.getCostMonthly());
        vo.setBillingCycleDay(bill.getBillingCycleDay());
        vo.setExpiresAt(bill.getExpiresAt());
    }

    static void enrichQuota(ServerLandingRespVO vo, ResourceServerQuotaDO quota, ResourceServerTrafficDO traffic) {
        if (ObjectUtil.isNull(vo)) return;
        if (ObjectUtil.isNotNull(quota)) {
            vo.setBandwidthMbps(quota.getBandwidthMbps());
            vo.setTotalGb(quota.getTotalGb());
            vo.setResetPolicy(quota.getResetPolicy());
        }
        if (ObjectUtil.isNotNull(traffic)) {
            vo.setUsedBytes(traffic.getUsedBytes());
            vo.setRxBytes(traffic.getRxBytes());
            vo.setTxBytes(traffic.getTxBytes());
            vo.setThrottleState(traffic.getThrottleState());
        }
    }

    static void enrichRuntime(ServerLandingRespVO vo, ResourceServerRuntimeDO runtime) {
        if (ObjectUtil.isNull(vo)) return;
        LocalDateTime lastHeartbeatAt = ObjectUtil.isNull(runtime) ? null : runtime.getLastHeartbeatAt();
        vo.setLastHeartbeatAt(lastHeartbeatAt);
        if (ObjectUtil.isNotNull(runtime)) {
            vo.setAgentVersion(runtime.getAgentVersion());
        }
        Long elapsedSec = ObjectUtil.isNull(lastHeartbeatAt) ? null
                : Duration.between(lastHeartbeatAt, LocalDateTime.now()).getSeconds();
        vo.setElapsedSec(elapsedSec);
        // 在线状态复用线路机同一套判定 (心跳延迟阈值 60/180/300s)
        vo.setOnlineState(AgentOnlineState.classify(elapsedSec).name());
    }
}
