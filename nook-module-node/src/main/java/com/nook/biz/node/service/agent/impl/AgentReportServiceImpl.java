package com.nook.biz.node.service.agent.impl;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.node.controller.agent.vo.AgentHeartbeatReqVO;
import com.nook.biz.node.controller.agent.vo.AgentNicTrafficReqVO;
import com.nook.biz.node.controller.agent.vo.AgentTaskResultReqVO;
import com.nook.biz.node.controller.agent.vo.AgentTaskRespVO;
import com.nook.biz.node.controller.agent.vo.AgentXrayTrafficReqVO;
import com.nook.biz.node.dal.dataobject.agent.AgentTaskDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.AgentTaskMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerCapacityMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerRuntimeMapper;
import com.nook.biz.node.service.agent.AgentReportService;
import com.nook.biz.node.service.agent.AgentRuntimeConfigService;
import com.nook.biz.node.service.xray.client.XrayClientTrafficSampleService;
import com.nook.biz.node.service.xray.client.XrayClientTrafficSampleService.AgentStatSnapshot;
import com.nook.biz.node.service.xray.client.XrayClientTrafficSampleService.SampleStat;
import com.nook.common.utils.collection.CollectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentReportServiceImpl implements AgentReportService {

    private static final double GB_BYTES = 1024.0 * 1024 * 1024;

    private final ResourceServerRuntimeMapper resourceServerRuntimeMapper;
    private final ResourceServerCapacityMapper resourceServerCapacityMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final XrayClientTrafficSampleService xrayClientTrafficSampleService;
    private final AgentRuntimeConfigService agentRuntimeConfigService;
    // service 仅用 onConfigReloadSuccess; 心跳的 yamlIfStale 已删 (改走 config_reload task)

    @Override
    public void receiveHeartbeat(ResourceServerDO server, AgentHeartbeatReqVO req, String clientIp) {
        int affected = resourceServerRuntimeMapper.onHeartbeat(
                server.getId(), LocalDateTime.now(),
                StrUtil.blankToDefault(req.getAgentVersion(), null),
                clientIp);
        if (affected == 0) {
            log.warn("[agent-hb] runtime 行不存在: serverId={}, 装机流程异常", server.getId());
        }
    }

    @Override
    public void receiveNicTraffic(ResourceServerDO server, AgentNicTrafficReqVO req) {
        long total = req.getRxBytes() + req.getTxBytes();
        resourceServerCapacityMapper.updateUsedTrafficBytes(server.getId(), total);
        // log 用 GB + 2 位小数, sub-GB 也能看清 (例如 0.46GB); int 截断 → 0 的老坑已修
        log.info("[agent-nic] serverId={} rx={} tx={} total={} period={}",
                server.getId(),
                String.format("%.2fGB", req.getRxBytes() / GB_BYTES),
                String.format("%.2fGB", req.getTxBytes() / GB_BYTES),
                String.format("%.2fGB", total / GB_BYTES),
                req.getPeriodStart());
        // TODO: 写 resource_server_traffic 历史表 (Sprint 1 拆按周期 UPSERT)
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AgentTaskRespVO> pullPendingTasks(ResourceServerDO server, int limit) {
        List<AgentTaskDO> pending = agentTaskMapper.selectPending(server.getId(), limit);
        if (CollectionUtils.isAnyEmpty(pending)) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        // 同事务里逐条 markPicked; CAS WHERE status=PENDING 防多 agent 并发拉 (理论上一 server 只跑一 agent, 防御性)
        return pending.stream()
                .filter(t -> agentTaskMapper.markPicked(t.getId(), now) > 0)
                .map(t -> {
                    AgentTaskRespVO vo = new AgentTaskRespVO();
                    vo.setId(t.getId());
                    vo.setTaskType(t.getTaskType());
                    vo.setTaskPayload(t.getTaskPayload());
                    return vo;
                })
                .toList();
    }

    @Override
    public void receiveTaskResult(ResourceServerDO server, AgentTaskResultReqVO req) {
        // agent_task.result_payload 是 JSON column; agent 可能上报裸字符串, 包装成 {"raw":"..."} 防 DataTruncation
        String payload = normalizeJsonOrWrap(req.getResultPayload());
        agentTaskMapper.markResult(req.getTaskId(), req.getStatus(), payload);
        log.info("[agent-task-result] serverId={} taskId={} status={}",
                server.getId(), req.getTaskId(), req.getStatus());
        // config_reload SUCCESS → 写 agent_runtime_config.applied_at + md5 (从原 task payload 取 md5)
        if ("SUCCESS".equals(req.getStatus())) {
            AgentTaskDO task = agentTaskMapper.selectById(req.getTaskId());
            if (task != null && "config_reload".equals(task.getTaskType())) {
                String md5 = extractField(task.getTaskPayload(), "md5");
                if (md5 != null) {
                    agentRuntimeConfigService.onConfigReloadSuccess(server.getId(), md5);
                }
            }
        }
    }

    private static String extractField(String json, String key) {
        if (json == null) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode n = JSON_VALIDATOR.readTree(json).get(key);
            return n == null || n.isNull() ? null : n.asText();
        } catch (Exception e) {
            return null;
        }
    }

    /** 上报的 payload 不一定是合法 JSON; 不合法时包成 {"raw":"..."}, 空值给 null. */
    private static String normalizeJsonOrWrap(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String trimmed = s.trim();
        char c = trimmed.charAt(0);
        if (c == '{' || c == '[' || c == '"' || c == 't' || c == 'f' || c == 'n'
                || c == '-' || (c >= '0' && c <= '9')) {
            try {
                JSON_VALIDATOR.readTree(trimmed);
                return trimmed;
            } catch (Exception ignore) { /* fall through */ }
        }
        try {
            return JSON_VALIDATOR.writeValueAsString(java.util.Map.of("raw", s));
        } catch (Exception e) {
            // 极端兜底: 完全没法编码, 落 null 也比抛 5xx 强
            return null;
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON_VALIDATOR =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @Override
    public void receiveXrayTraffic(ResourceServerDO server, AgentXrayTrafficReqVO req) {
        if (req.getStats() == null || req.getStats().isEmpty()) {
            log.debug("[agent-xray-traffic] serverId={} 空 stats, 跳过", server.getId());
            return;
        }
        Map<String, AgentStatSnapshot> snapshot = new HashMap<>(req.getStats().size());
        for (AgentXrayTrafficReqVO.Row row : req.getStats()) {
            snapshot.put(row.getEmail(),
                    new AgentStatSnapshot(row.getUpBytes(), row.getDownBytes()));
        }
        SampleStat stat = xrayClientTrafficSampleService.applyAgentStats(server.getId(), snapshot);
        log.info("[agent-xray-traffic] serverId={} 上报={} 入库={} 孤儿={}",
                server.getId(), req.getStats().size(), stat.upserted(), stat.skipped());
    }
}
