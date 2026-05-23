package com.nook.biz.agent.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nook.biz.agent.api.enums.AgentHostType;
import com.nook.biz.agent.api.enums.AgentTaskType;
import com.nook.biz.agent.dal.dataobject.AgentRuntimeConfigDO;
import com.nook.biz.agent.dal.mysql.mapper.AgentRuntimeConfigMapper;
import com.nook.biz.agent.service.AgentRuntimeConfigService;
import com.nook.biz.agent.service.AgentTaskDispatchService;
import com.nook.biz.agent.validator.AgentRuntimeConfigValidator;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
@RequiredArgsConstructor
public class AgentRuntimeConfigServiceImpl implements AgentRuntimeConfigService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AgentRuntimeConfigMapper agentRuntimeConfigMapper;
    private final ResourceServerApi resourceServerApi;
    private final AgentTaskDispatchService agentTaskDispatchService;
    private final AgentRuntimeConfigValidator agentRuntimeConfigValidator;

    @Override
    public AgentRuntimeConfigDO get(String serverId) {
        return agentRuntimeConfigMapper.selectById(serverId);
    }

    @Override
    public String save(String serverId, String yaml, String operatorId) {
        resourceServerApi.validateExists(serverId);
        agentRuntimeConfigValidator.validateYaml(yaml);
        AgentRuntimeConfigDO existing = agentRuntimeConfigMapper.selectById(serverId);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            AgentRuntimeConfigDO row = new AgentRuntimeConfigDO();
            row.setServerId(serverId);
            row.setConfigYaml(yaml);
            row.setUpdatedAt(now);
            row.setUpdatedBy(operatorId);
            agentRuntimeConfigMapper.insert(row);
        } else {
            agentRuntimeConfigMapper.updateConfigYaml(serverId, yaml, now, operatorId);
        }
        String md5 = md5(yaml);
        String payload;
        try {
            payload = JSON.writeValueAsString(Map.of("yaml", yaml, "md5", md5));
        } catch (JsonProcessingException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "task payload 序列化失败");
        }
        String taskId = agentTaskDispatchService.dispatch(AgentHostType.SERVER, serverId, AgentTaskType.CONFIG_RELOAD.getCode(), payload);
        log.info("[save] serverId={} taskId={} bytes={} operator={}",
                serverId, taskId, yaml.length(), operatorId);
        return taskId;
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
