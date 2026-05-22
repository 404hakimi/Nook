package com.nook.biz.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nook.biz.agent.controller.vo.AgentHeartbeatReqVO;
import com.nook.biz.agent.controller.vo.AgentNicTrafficReqVO;
import com.nook.biz.agent.controller.vo.AgentTaskResultReqVO;
import com.nook.biz.agent.controller.vo.AgentTaskRespVO;
import com.nook.biz.agent.controller.vo.AgentXrayTrafficReqVO;
import com.nook.biz.agent.dal.dataobject.AgentTaskDO;
import com.nook.biz.agent.dal.mysql.mapper.AgentTaskMapper;
import com.nook.biz.agent.api.enums.AgentTaskStatus;
import com.nook.biz.agent.api.enums.AgentTaskType;
import com.nook.biz.agent.service.AgentReportService;
import com.nook.biz.agent.service.AgentRuntimeConfigService;
import com.nook.biz.node.api.resource.ResourceServerCapacityApi;
import com.nook.biz.node.api.resource.ResourceServerRuntimeApi;
import com.nook.biz.node.api.xray.XrayClientTrafficSampleApi;
import com.nook.biz.node.api.xray.dto.AgentStatSnapshotDTO;
import com.nook.biz.node.api.xray.dto.SampleStatDTO;
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
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ResourceServerRuntimeApi resourceServerRuntimeApi;
    private final ResourceServerCapacityApi resourceServerCapacityApi;
    private final AgentTaskMapper agentTaskMapper;
    private final XrayClientTrafficSampleApi xrayClientTrafficSampleApi;
    private final AgentRuntimeConfigService agentRuntimeConfigService;

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
        long total = req.getRxBytes() + req.getTxBytes();
        resourceServerCapacityApi.addUsedTrafficBytes(serverId, total);
        log.info("[receiveNicTraffic] serverId={} rx={} tx={} total={} period={}",
                serverId,
                String.format("%.2fGB", req.getRxBytes() / GB_BYTES),
                String.format("%.2fGB", req.getTxBytes() / GB_BYTES),
                String.format("%.2fGB", total / GB_BYTES),
                req.getPeriodStart());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AgentTaskRespVO> pullPendingTasks(String serverId, int limit) {
        List<AgentTaskDO> pending = agentTaskMapper.selectPending(serverId, limit);
        if (CollectionUtils.isAnyEmpty(pending)) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        // markPicked 是 CAS WHERE status=PENDING; 防多 agent 并发拉同一任务 (理论上一 server 只跑一 agent, 防御性)
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
    public void receiveTaskResult(String serverId, AgentTaskResultReqVO req) {
        // agent 可能上报裸字符串, 包装成 {"raw":"..."} 防 result_payload (JSON 列) DataTruncation
        String payload = normalizeJsonOrWrap(req.getResultPayload());
        agentTaskMapper.markResult(req.getTaskId(), req.getStatus(), payload);
        log.info("[receiveTaskResult] serverId={} taskId={} status={}",
                serverId, req.getTaskId(), req.getStatus());
        if (AgentTaskStatus.SUCCESS.name().equals(req.getStatus())) {
            AgentTaskDO task = agentTaskMapper.selectById(req.getTaskId());
            if (task != null && AgentTaskType.CONFIG_RELOAD.getCode().equals(task.getTaskType())) {
                String md5 = extractField(task.getTaskPayload(), "md5");
                if (md5 != null) {
                    agentRuntimeConfigService.onConfigReloadSuccess(serverId, md5);
                }
            }
        }
    }

    @Override
    public void receiveXrayTraffic(String serverId, AgentXrayTrafficReqVO req) {
        if (req.getStats() == null || req.getStats().isEmpty()) {
            log.debug("[receiveXrayTraffic] serverId={} 空 stats, 跳过", serverId);
            return;
        }
        Map<String, AgentStatSnapshotDTO> snapshot = new HashMap<>(req.getStats().size());
        for (AgentXrayTrafficReqVO.Row row : req.getStats()) {
            snapshot.put(row.getEmail(), new AgentStatSnapshotDTO(row.getUpBytes(), row.getDownBytes()));
        }
        SampleStatDTO stat = xrayClientTrafficSampleApi.applyAgentStats(serverId, snapshot);
        log.info("[receiveXrayTraffic] serverId={} 上报={} 入库={} 孤儿={}",
                serverId, req.getStats().size(), stat.upserted(), stat.skipped());
    }

    private static String extractField(String json, String key) {
        if (json == null) return null;
        try {
            JsonNode n = JSON.readTree(json).get(key);
            return n == null || n.isNull() ? null : n.asText();
        } catch (Exception e) {
            return null;
        }
    }

    /** 不合法 JSON 包成 {"raw":"..."}; 空值给 null. */
    private static String normalizeJsonOrWrap(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String trimmed = s.trim();
        char c = trimmed.charAt(0);
        if (c == '{' || c == '[' || c == '"' || c == 't' || c == 'f' || c == 'n'
                || c == '-' || (c >= '0' && c <= '9')) {
            try {
                JSON.readTree(trimmed);
                return trimmed;
            } catch (Exception ignore) { /* fall through */ }
        }
        try {
            return JSON.writeValueAsString(Map.of("raw", s));
        } catch (Exception e) {
            return null;
        }
    }
}
