package com.nook.biz.agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nook.biz.agent.dal.dataobject.AgentRuntimeConfigDO;
import com.nook.biz.agent.dal.mysql.mapper.AgentRuntimeConfigMapper;
import com.nook.biz.agent.api.enums.AgentTaskType;
import com.nook.biz.agent.service.AgentRuntimeConfigService;
import com.nook.biz.agent.service.AgentTaskDispatchService;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRuntimeConfigServiceImpl implements AgentRuntimeConfigService {

    private final AgentRuntimeConfigMapper mapper;
    private final ResourceServerApi resourceServerApi;
    private final AgentTaskDispatchService agentTaskDispatchService;
    private static final Yaml YAML = new Yaml();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public AgentRuntimeConfigDO get(String serverId) {
        return mapper.selectById(serverId);
    }

    @Override
    public String save(String serverId, String yaml, String operatorId) {
        resourceServerApi.validateExists(serverId);
        if (yaml == null || yaml.isBlank()) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "yaml 不能为空");
        }
        try {
            Object parsed = YAML.load(yaml);
            if (!(parsed instanceof java.util.Map)) {
                throw new BusinessException(CommonErrorCode.PARAM_INVALID, "yaml 顶层必须是 object");
            }
        } catch (YAMLException ye) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "yaml 语法错误: " + ye.getMessage());
        }
        AgentRuntimeConfigDO existing = mapper.selectById(serverId);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            AgentRuntimeConfigDO row = new AgentRuntimeConfigDO();
            row.setServerId(serverId);
            row.setConfigYaml(yaml);
            row.setUpdatedAt(now);
            row.setUpdatedBy(operatorId);
            mapper.insert(row);
        } else {
            mapper.update(null, Wrappers.<AgentRuntimeConfigDO>lambdaUpdate()
                    .set(AgentRuntimeConfigDO::getConfigYaml, yaml)
                    .set(AgentRuntimeConfigDO::getUpdatedAt, now)
                    .set(AgentRuntimeConfigDO::getUpdatedBy, operatorId)
                    .eq(AgentRuntimeConfigDO::getServerId, serverId));
        }
        String md5 = md5(yaml);
        String payload;
        try {
            payload = JSON.writeValueAsString(Map.of("yaml", yaml, "md5", md5));
        } catch (JsonProcessingException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "task payload 序列化失败");
        }
        String taskId = agentTaskDispatchService.dispatch(serverId, AgentTaskType.CONFIG_RELOAD.getCode(), payload);
        log.info("[save] serverId={} taskId={} bytes={} operator={}",
                serverId, taskId, yaml.length(), operatorId);
        return taskId;
    }

    @Override
    public void onConfigReloadSuccess(String serverId, String appliedYamlMd5) {
        mapper.update(null, Wrappers.<AgentRuntimeConfigDO>lambdaUpdate()
                .set(AgentRuntimeConfigDO::getAppliedAt, LocalDateTime.now())
                .set(AgentRuntimeConfigDO::getAppliedYamlMd5, appliedYamlMd5)
                .eq(AgentRuntimeConfigDO::getServerId, serverId));
        log.info("[onConfigReloadSuccess] serverId={} md5={}", serverId, appliedYamlMd5);
    }

    private static String md5(String s) {
        if (s == null) return "";
        return DigestUtils.md5DigestAsHex(s.getBytes(StandardCharsets.UTF_8));
    }
}
