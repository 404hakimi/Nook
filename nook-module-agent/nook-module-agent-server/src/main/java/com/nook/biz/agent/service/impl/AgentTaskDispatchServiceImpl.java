package com.nook.biz.agent.service.impl;

import com.nook.biz.agent.dal.dataobject.AgentTaskDO;
import com.nook.biz.agent.dal.mysql.mapper.AgentTaskMapper;
import com.nook.biz.agent.api.enums.AgentTaskStatus;
import com.nook.biz.agent.service.AgentTaskDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskDispatchServiceImpl implements AgentTaskDispatchService {

    private final AgentTaskMapper agentTaskMapper;

    @Override
    public String dispatch(String serverId, String taskType, String payloadJson) {
        AgentTaskDO task = new AgentTaskDO();
        task.setServerId(serverId);
        task.setTaskType(taskType);
        task.setTaskPayload(payloadJson == null ? "{}" : payloadJson);
        task.setStatus(AgentTaskStatus.PENDING.name());
        task.setRetryCount(0);
        agentTaskMapper.insert(task);
        log.info("[dispatch] serverId={} type={} taskId={}", serverId, taskType, task.getId());
        return task.getId();
    }
}
