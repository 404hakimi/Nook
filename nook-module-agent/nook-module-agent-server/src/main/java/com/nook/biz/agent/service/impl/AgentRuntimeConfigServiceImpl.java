package com.nook.biz.agent.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nook.biz.agent.api.enums.AgentConfigSyncState;
import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.agent.api.enums.AgentTaskType;
import com.nook.biz.agent.dal.dataobject.AgentRuntimeConfigDO;
import com.nook.biz.agent.dal.mysql.mapper.AgentRuntimeConfigMapper;
import com.nook.biz.agent.service.AgentRuntimeConfigService;
import com.nook.biz.agent.service.AgentTaskDispatchService;
import com.nook.biz.agent.validator.AgentRuntimeConfigValidator;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 运行时配置 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
public class AgentRuntimeConfigServiceImpl implements AgentRuntimeConfigService {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private AgentRuntimeConfigMapper agentRuntimeConfigMapper;
    @Resource
    private ResourceServerApi resourceServerApi;
    @Resource
    private AgentTaskDispatchService agentTaskDispatchService;
    @Resource
    private AgentRuntimeConfigValidator agentRuntimeConfigValidator;

    @Override
    public AgentRuntimeConfigDO get(String serverId) {
        return agentRuntimeConfigMapper.selectById(serverId);
    }

    @Override
    public AgentConfigSyncState classifySyncState(AgentRuntimeConfigDO row) {
        if (row == null) {
            return AgentConfigSyncState.NEVER_CONFIGURED;
        }
        return md5(row.getConfigYaml()).equals(row.getAppliedYamlMd5())
                ? AgentConfigSyncState.SYNCED
                : AgentConfigSyncState.PENDING;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(String serverId, String yaml, String operatorId) {
        resourceServerApi.validateExists(serverId);
        agentRuntimeConfigValidator.validateYaml(yaml);
        AgentRuntimeConfigDO existing = agentRuntimeConfigMapper.selectById(serverId);
        if (existing == null) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID,
                    "agent 尚未装机 (DB 无 agent_runtime_config 行), 走装机流程而非改配置");
        }
        // agentType 装机时已定下, admin 改 yaml 不改 role
        AgentRole agentType = AgentRole.fromCode(existing.getAgentType());
        LocalDateTime now = LocalDateTime.now();
        agentRuntimeConfigMapper.updateConfigYaml(serverId, yaml, now, operatorId);
        String md5 = md5(yaml);
        String payload;
        try {
            payload = JSON.writeValueAsString(Map.of("yaml", yaml, "md5", md5));
        } catch (JsonProcessingException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "task payload 序列化失败");
        }
        String taskId = agentTaskDispatchService.dispatch(agentType, serverId, AgentTaskType.CONFIG_RELOAD.getCode(), payload);
        log.info("[save] serverId={} agent={} taskId={} bytes={} operator={}",
                serverId, agentType.getCode(), taskId, yaml.length(), operatorId);
        return taskId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordAsSynced(String serverId, AgentRole agentType, String yaml, String operatorId) {
        agentRuntimeConfigValidator.validateYaml(yaml);
        LocalDateTime now = LocalDateTime.now();
        String yamlMd5 = md5(yaml);
        AgentRuntimeConfigDO existing = agentRuntimeConfigMapper.selectById(serverId);
        if (existing == null) {
            AgentRuntimeConfigDO row = new AgentRuntimeConfigDO();
            row.setServerId(serverId);
            row.setAgentType(agentType.getCode());
            row.setConfigYaml(yaml);
            row.setUpdatedAt(now);
            row.setUpdatedBy(operatorId);
            row.setAppliedAt(now);
            row.setAppliedYamlMd5(yamlMd5);
            agentRuntimeConfigMapper.insert(row);
        } else {
            // 重装场景: 覆盖现有 yaml 并把 applied_md5 同步刷新 (SYNCED), 不留 PENDING 漂移
            agentRuntimeConfigMapper.updateConfigYaml(serverId, yaml, now, operatorId);
            agentRuntimeConfigMapper.updateApplied(serverId, now, yamlMd5);
        }
        log.info("[recordAsSynced] serverId={} agent={} bytes={} operator={}",
                serverId, agentType.getCode(), yaml.length(), operatorId);
    }

    @Override
    public void onConfigReloadSuccess(String serverId, String appliedYamlMd5) {
        agentRuntimeConfigMapper.updateApplied(serverId, LocalDateTime.now(), appliedYamlMd5);
        log.info("[onConfigReloadSuccess] serverId={} md5={}", serverId, appliedYamlMd5);
    }

    private static String md5(String s) {
        if (s == null) return "";
        return DigestUtils.md5DigestAsHex(s.getBytes(StandardCharsets.UTF_8));
    }
}
