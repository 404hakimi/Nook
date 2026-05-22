package com.nook.biz.agent.dal.mysql.mapper;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.agent.controller.admin.vo.AdminAgentTaskPageReqVO;
import com.nook.biz.agent.dal.dataobject.AgentTaskDO;
import com.nook.biz.agent.api.enums.AgentTaskStatus;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 任务 Mapper
 *
 * @author nook
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTaskDO> {

    /** 拉某 server 的 PENDING 任务 (≤ N 条, FIFO). */
    default List<AgentTaskDO> selectPending(String serverId, int limit) {
        return selectList(Wrappers.<AgentTaskDO>lambdaQuery()
                .eq(AgentTaskDO::getServerId, serverId)
                .eq(AgentTaskDO::getStatus, AgentTaskStatus.PENDING.name())
                .orderByAsc(AgentTaskDO::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 50))));
    }

    /** 标记任务为 PICKED (agent 拉走时调用). */
    default int markPicked(String id, LocalDateTime at) {
        return update(null, Wrappers.<AgentTaskDO>lambdaUpdate()
                .set(AgentTaskDO::getStatus, AgentTaskStatus.PICKED.name())
                .set(AgentTaskDO::getPickedAt, at)
                .set(AgentTaskDO::getUpdatedAt, LocalDateTime.now())
                .eq(AgentTaskDO::getId, id)
                .eq(AgentTaskDO::getStatus, AgentTaskStatus.PENDING.name()));
    }

    /** 标记任务为 SUCCESS / FAILED + 写回结果. */
    default int markResult(String id, String status, String resultPayload) {
        return update(null, Wrappers.<AgentTaskDO>lambdaUpdate()
                .set(AgentTaskDO::getStatus, status)
                .set(AgentTaskDO::getResultPayload, resultPayload)
                .set(AgentTaskDO::getUpdatedAt, LocalDateTime.now())
                .eq(AgentTaskDO::getId, id));
    }

    /** Admin 看某 server 的 task 分页 (倒序); 支持类型 + 状态可选筛选. */
    default IPage<AgentTaskDO> selectPageByServer(IPage<AgentTaskDO> page,
                                                  String serverId,
                                                  AdminAgentTaskPageReqVO reqVO) {
        return selectPage(page, Wrappers.<AgentTaskDO>lambdaQuery()
                .eq(AgentTaskDO::getServerId, serverId)
                .eq(StrUtil.isNotBlank(reqVO.getTaskType()), AgentTaskDO::getTaskType, reqVO.getTaskType())
                .eq(StrUtil.isNotBlank(reqVO.getStatus()), AgentTaskDO::getStatus, reqVO.getStatus())
                .orderByDesc(AgentTaskDO::getCreatedAt));
    }
}
