package com.nook.biz.node.service.agent;

import com.nook.biz.node.controller.agent.admin.vo.AdminAgentDetailRespVO;
import com.nook.biz.node.controller.agent.admin.vo.AdminAgentListItemRespVO;
import com.nook.biz.node.controller.agent.admin.vo.AdminAgentTaskRespVO;
import com.nook.biz.node.controller.agent.admin.vo.AdminTruncateLogReqVO;

import java.util.List;

/** Admin Agent 管理 service; 拼接 server + runtime + capacity 数据. */
public interface AdminAgentService {

    List<AdminAgentListItemRespVO> list();

    AdminAgentDetailRespVO detail(String serverId);

    /** 派 agent_upgrade task; agent 拉 backend 当前 FS 上的 binary. 返回 taskId. */
    String dispatchUpgrade(String serverId);

    /** 派 truncate_log task; 返回 taskId. */
    String dispatchTruncateLog(String serverId, AdminTruncateLogReqVO req);

    /** Admin 查某 server 最近 N 条 task; 用于 UI 任务历史. */
    List<AdminAgentTaskRespVO> recentTasks(String serverId, int limit);
}
