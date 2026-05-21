package com.nook.biz.node.service.agent.impl;

import com.nook.biz.node.dal.dataobject.agent.AgentTaskDO;
import com.nook.biz.node.dal.mysql.mapper.AgentTaskMapper;
import com.nook.biz.node.service.agent.AgentTaskDispatchService;
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
        task.setStatus("PENDING");
        task.setRetryCount(0);
        agentTaskMapper.insert(task);
        log.info("[dispatch] serverId={} type={} taskId={}", serverId, taskType, task.getId());
        return task.getId();
    }
}
