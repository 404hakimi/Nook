package com.nook.biz.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.agent.controller.vo.AgentHeartbeatReqVO;
import com.nook.biz.agent.controller.vo.AgentNicTrafficReqVO;
import com.nook.biz.agent.service.AgentReportService;
import com.nook.biz.node.api.resource.ResourceServerCapacityApi;
import com.nook.biz.node.api.resource.ResourceServerLandingApi;
import com.nook.biz.node.api.resource.ResourceServerRuntimeApi;
import com.nook.biz.node.api.xray.XrayClientReconcileApi;
import com.nook.biz.node.api.xray.dto.XrayReconcileClientDTO;
import com.nook.biz.trade.api.TradeBandwidthApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 上报数据 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class AgentReportServiceImpl implements AgentReportService {

    private static final double GB_BYTES = 1024.0 * 1024 * 1024;

    @Resource
    private ResourceServerRuntimeApi resourceServerRuntimeApi;
    @Resource
    private ResourceServerCapacityApi resourceServerCapacityApi;
    @Resource
    private XrayClientReconcileApi xrayClientReconcileApi;
    @Resource
    private TradeBandwidthApi tradeBandwidthApi;
    @Resource
    private ResourceServerLandingApi resourceServerLandingApi;

    @Override
    public void receiveHeartbeat(String serverId, AgentHeartbeatReqVO req, String clientIp) {
        int affected = resourceServerRuntimeApi.onHeartbeat(
                serverId, LocalDateTime.now(),
                StrUtil.blankToDefault(req.getAgentVersion(), null),
                clientIp);
        if (affected == 0) {
            log.warn("[receiveHeartbeat] runtime 行不存在 serverId={}, 装机流程异常", serverId);
        }
    }

    @Override
    public void receiveNicTraffic(String serverId, AgentNicTrafficReqVO req) {
        resourceServerCapacityApi.applyNicTraffic(serverId, req.getRxBytes(), req.getTxBytes(), req.getBizUsedBytes());
        log.info("[receiveNicTraffic] serverId={} rx={} tx={} period={}",
                serverId,
                String.format("%.2fGB", req.getRxBytes() / GB_BYTES),
                String.format("%.2fGB", req.getTxBytes() / GB_BYTES),
                req.getPeriodStart());
    }

    @Override
    public List<XrayReconcileClientDTO> getDesiredClients(String serverId) {
        return xrayClientReconcileApi.getDesiredClients(serverId);
    }

    @Override
    public int getLandingDesiredBandwidthMbps(String serverId) {
        return tradeBandwidthApi.getLandingDesiredBandwidthMbps(serverId);
    }

    @Override
    public int getLandingSocks5Port(String serverId) {
        return resourceServerLandingApi.getSocks5Port(serverId);
    }
}
