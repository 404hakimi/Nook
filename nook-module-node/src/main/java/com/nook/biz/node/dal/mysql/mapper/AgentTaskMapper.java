package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.agent.AgentTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/** AgentTask 数据访问 (任务队列). */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTaskDO> {

    /** 拉某 server 的 PENDING 任务 (≤ N 条, FIFO). */
    default List<AgentTaskDO> selectPending(String serverId, int limit) {
        return selectList(Wrappers.<AgentTaskDO>lambdaQuery()
                .eq(AgentTaskDO::getServerId, serverId)
                .eq(AgentTaskDO::getStatus, "PENDING")
                .orderByAsc(AgentTaskDO::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 50))));
    }

    /** 标记任务为 PICKED (agent 拉走时调用). */
    default int markPicked(String id, LocalDateTime at) {
        return update(null, Wrappers.<AgentTaskDO>lambdaUpdate()
                .set(AgentTaskDO::getStatus, "PICKED")
                .set(AgentTaskDO::getPickedAt, at)
                .set(AgentTaskDO::getUpdatedAt, LocalDateTime.now())
                .eq(AgentTaskDO::getId, id)
                .eq(AgentTaskDO::getStatus, "PENDING"));
    }

    /** 标记任务为 SUCCESS / FAILED + 写回结果. */
    default int markResult(String id, String status, String resultPayload) {
        return update(null, Wrappers.<AgentTaskDO>lambdaUpdate()
                .set(AgentTaskDO::getStatus, status)
                .set(AgentTaskDO::getResultPayload, resultPayload)
                .set(AgentTaskDO::getUpdatedAt, LocalDateTime.now())
                .eq(AgentTaskDO::getId, id));
    }

    /** Admin 看某 server 最近 N 条 task (倒序); UI 任务历史用. */
    default List<AgentTaskDO> selectRecentByServer(String serverId, int limit) {
        return selectList(Wrappers.<AgentTaskDO>lambdaQuery()
                .eq(AgentTaskDO::getServerId, serverId)
                .orderByDesc(AgentTaskDO::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }
}
