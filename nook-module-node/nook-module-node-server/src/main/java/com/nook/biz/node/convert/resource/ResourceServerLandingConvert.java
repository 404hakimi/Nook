package com.nook.biz.node.convert.resource;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingBillingRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingQuotaRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingInstallRespVO;
import com.nook.biz.agent.api.enums.AgentOnlineState;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingListItemRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingSocks5RespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingSummaryRespVO;
import com.nook.biz.node.entity.ResourceServerBillingDO;
import com.nook.biz.node.entity.ResourceServerQuotaDO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.entity.Socks5InstallDO;
import com.nook.biz.node.entity.ResourceServerRuntimeDO;
import com.nook.biz.node.entity.ResourceServerTrafficDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Mapper
public interface ResourceServerLandingConvert {

    ResourceServerLandingConvert INSTANCE = Mappers.getMapper(ResourceServerLandingConvert.class);

    default ServerLandingSummaryRespVO toSummaryRespVO(Map<String, Long> raw) {
        ServerLandingSummaryRespVO vo = new ServerLandingSummaryRespVO();
        vo.setTotal(raw.getOrDefault("total", 0L));
        vo.setInstalling(raw.getOrDefault("lifecycle_INSTALLING", 0L));
        vo.setReady(raw.getOrDefault("lifecycle_READY", 0L));
        vo.setLive(raw.getOrDefault("lifecycle_LIVE", 0L));
        vo.setRetired(raw.getOrDefault("lifecycle_RETIRED", 0L));
        vo.setAvailable(raw.getOrDefault("status_AVAILABLE", 0L));
        vo.setOccupied(raw.getOrDefault("status_OCCUPIED", 0L));
        return vo;
    }

    ServerLandingRespVO convert(ResourceServerDO bean);

    ServerLandingSocks5RespVO toSocks5RespVO(Socks5InstallDO landing);

    ServerLandingInstallRespVO toInstallRespVO(Socks5InstallDO landing);

    ServerLandingBillingRespVO toBillingRespVO(ResourceServerBillingDO bill);

    default ServerLandingQuotaRespVO toQuotaRespVO(ResourceServerQuotaDO quota, ResourceServerTrafficDO traffic) {
        ServerLandingQuotaRespVO vo = new ServerLandingQuotaRespVO();
        if (ObjectUtil.isNull(quota)) {
            return vo;
        }
        vo.setServerId(quota.getServerId());
        vo.setBandwidthMbps(quota.getBandwidthMbps());
        vo.setTotalGb(quota.getTotalGb());
        vo.setUsablePercent(quota.getUsablePercent());
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
