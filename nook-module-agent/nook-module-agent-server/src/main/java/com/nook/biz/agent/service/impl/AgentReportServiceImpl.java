package com.nook.biz.agent.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.agent.controller.vo.AgentHeartbeatReqVO;
import com.nook.biz.agent.controller.vo.AgentNicTrafficReqVO;
import com.nook.biz.agent.controller.vo.LandingDesiredRespVO;
import com.nook.biz.agent.service.AgentReportService;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.ResourceServerCapacityApi;
import com.nook.biz.node.api.resource.ResourceServerLandingApi;
import com.nook.biz.node.api.resource.ResourceServerRuntimeApi;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.api.xray.XrayClientReconcileApi;
import com.nook.biz.node.api.xray.dto.XrayReconcileClientDTO;
import com.nook.biz.trade.api.TradeBandwidthApi;
import com.nook.common.utils.unit.TrafficUnitUtils;
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

    @Resource
    private ResourceServerRuntimeApi resourceServerRuntimeApi;
    @Resource
    private ResourceServerCapacityApi resourceServerCapacityApi;
    @Resource
    private ResourceServerApi resourceServerApi;
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
        // 获取服务器信息
        ResourceServerRespDTO server = resourceServerApi.getServer(serverId);
        if (ObjectUtil.isNull(server)) {
            log.warn("[Agent流量上报] 服务器信息不存在 serverId={}", serverId);
            return;
        }
        String ipAddress = StrUtil.isBlank(server.getIpAddress()) ? "-" : server.getIpAddress();
        // 覆盖写入网卡周期累计字节
        resourceServerCapacityApi.applyNicTraffic(serverId, req.getRxBytes(), req.getTxBytes(), req.getBizUsedBytes());
        // 根据服务器类型进行输出对应的日志
        if (ResourceServerTypeEnum.FRONTLINE.getState().equals(server.getServerType())) {
            log.info("[Agent流量上报] {}>>>[{}]({}) 入站={}GB 出站={}GB", ResourceServerTypeEnum.FRONTLINE.getState(), server.getName(), ipAddress,
                    TrafficUnitUtils.toGb(req.getRxBytes()), TrafficUnitUtils.toGb(req.getTxBytes()));
        } else {
            Long biz = req.getBizUsedBytes();
            String bizText = ObjectUtil.isNull(biz) ? "0MB" : TrafficUnitUtils.toMb(biz) + "MB";
            log.info("[Agent流量上报] {}>>>[{}]({}) 入站={}GB 出站={}GB 业务流量={}", ResourceServerTypeEnum.LANDING.getState(), server.getName(), ipAddress,
                    TrafficUnitUtils.toGb(req.getRxBytes()), TrafficUnitUtils.toGb(req.getTxBytes()), bizText);
        }
    }

    @Override
    public List<XrayReconcileClientDTO> getDesiredClients(String serverId) {
        return xrayClientReconcileApi.getDesiredClients(serverId);
    }

    @Override
    public LandingDesiredRespVO getLandingDesired(String serverId) {
        // 出口限速: 取占用订阅的套餐带宽与落地机带宽上限的较小值
        int bandwidthMbps = tradeBandwidthApi.getLandingDesiredBandwidthMbps(serverId);
        // socks5 端口: agent 建 nft 业务流量计数器用
        int socks5Port = resourceServerLandingApi.getSocks5Port(serverId);
        return LandingDesiredRespVO.builder()
                .bandwidthMbps(bandwidthMbps)
                .socks5Port(socks5Port)
                .build();
    }
}
