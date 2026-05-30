package com.nook.biz.node.convert.resource;

import com.nook.biz.node.controller.resource.vo.ServerLandingBillingRespVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingCapacityRespVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingInstallRespVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingRespVO;
import com.nook.biz.node.controller.resource.vo.ServerLandingSocks5RespVO;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerBillingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerCapacityDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerLandingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
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
    LandingSummaryDTO toSummary(ResourceServerDO server, ResourceServerLandingDO landing);

    /** 主表 → RespVO (仅主表字段; 子表字段需走 enrich) */
    ServerLandingRespVO convert(ResourceServerDO bean);

    /** landing 子表 → SOCKS5 配置 VO */
    ServerLandingSocks5RespVO toSocks5RespVO(ResourceServerLandingDO landing);

    /** landing 子表 → 装机事实 VO */
    ServerLandingInstallRespVO toInstallRespVO(ResourceServerLandingDO landing);

    /** billing 子表 → 账面 VO */
    ServerLandingBillingRespVO toBillingRespVO(ResourceServerBillingDO bill);

    /** capacity 子表 → 容量监控 VO */
    ServerLandingCapacityRespVO toCapacityRespVO(ResourceServerCapacityDO cap);

    /** 详情聚合: 主表 + 4 子表 → RespVO (SSH 凭据走公共 /get-credential, 不在此 VO) */
    default ServerLandingRespVO convertWithSubtables(ResourceServerDO main,
                                                    ResourceServerLandingDO landing,
                                                    ResourceServerBillingDO bill,
                                                    ResourceServerCapacityDO cap,
                                                    ResourceServerRuntimeDO runtime) {
        ServerLandingRespVO vo = convert(main);
        enrichLanding(vo, landing);
        enrichBilling(vo, bill);
        enrichCapacity(vo, cap);
        enrichRuntime(vo, runtime);
        return vo;
    }

    /** 列表聚合: 主表分页 + 4 子表 Map → 分页 RespVO */
    default PageResult<ServerLandingRespVO> convertPageWithSubtables(
            PageResult<ResourceServerDO> page,
            Map<String, ResourceServerLandingDO> landingMap,
            Map<String, ResourceServerBillingDO> billMap,
            Map<String, ResourceServerCapacityDO> capMap,
            Map<String, ResourceServerRuntimeDO> runtimeMap) {
        List<ResourceServerDO> records = page.getRecords();
        List<ServerLandingRespVO> list = new ArrayList<>(records.size());
        for (ResourceServerDO srv : records) {
            ServerLandingRespVO vo = convert(srv);
            enrichLanding(vo, landingMap == null ? null : landingMap.get(srv.getId()));
            enrichBilling(vo, billMap == null ? null : billMap.get(srv.getId()));
            enrichCapacity(vo, capMap == null ? null : capMap.get(srv.getId()));
            enrichRuntime(vo, runtimeMap == null ? null : runtimeMap.get(srv.getId()));
            list.add(vo);
        }
        return PageResult.of(page.getTotal(), list);
    }

    static void enrichLanding(ServerLandingRespVO vo, ResourceServerLandingDO landing) {
        if (vo == null || landing == null) return;
        vo.setIpTypeId(landing.getIpTypeId());
        vo.setSocks5Port(landing.getSocks5Port());
        vo.setSocks5Username(landing.getSocks5Username());
        vo.setSocks5Password(landing.getSocks5Password());
        vo.setStatus(landing.getStatus());
        vo.setOccupiedByMemberId(landing.getOccupiedByMemberId());
        vo.setOccupiedAt(landing.getOccupiedAt());
        vo.setCoolingUntil(landing.getCoolingUntil());
        vo.setReservedExpiresAt(landing.getReservedExpiresAt());
        vo.setAssignCount(landing.getAssignCount());
        vo.setProvisionMode(landing.getProvisionMode());
        vo.setLogLevel(landing.getLogLevel());
        vo.setLogPath(landing.getLogPath());
        vo.setAutostartEnabled(landing.getAutostartEnabled());
        vo.setFirewallEnabled(landing.getFirewallEnabled());
        vo.setInstallDir(landing.getInstallDir());
        vo.setInstalledAt(landing.getInstalledAt());
    }

    static void enrichBilling(ServerLandingRespVO vo, ResourceServerBillingDO bill) {
        if (vo == null || bill == null) return;
        vo.setCostMonthly(bill.getCostMonthly());
        vo.setBillingCycleDay(bill.getBillingCycleDay());
        vo.setExpiresAt(bill.getExpiresAt());
    }

    static void enrichCapacity(ServerLandingRespVO vo, ResourceServerCapacityDO cap) {
        if (vo == null || cap == null) return;
        vo.setBandwidthLimitMbps(cap.getBandwidthLimitMbps());
        vo.setMonthlyTrafficGb(cap.getMonthlyTrafficGb());
        vo.setUsedTrafficBytes(cap.getUsedTrafficBytes());
        vo.setRxBytes(cap.getRxBytes());
        vo.setTxBytes(cap.getTxBytes());
        vo.setQuotaResetPolicy(cap.getQuotaResetPolicy());
        vo.setThrottleState(cap.getThrottleState());
    }

    static void enrichRuntime(ServerLandingRespVO vo, ResourceServerRuntimeDO runtime) {
        if (vo == null || runtime == null) return;
        vo.setLastHeartbeatAt(runtime.getLastHeartbeatAt());
    }
}
