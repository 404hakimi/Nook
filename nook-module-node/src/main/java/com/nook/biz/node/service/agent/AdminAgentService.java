package com.nook.biz.node.service.agent;

import com.nook.biz.node.controller.agent.admin.vo.AdminAgentDetailRespVO;
import com.nook.biz.node.controller.agent.admin.vo.AdminAgentListItemRespVO;
import com.nook.biz.node.controller.agent.admin.vo.AdminAgentTaskPageReqVO;
import com.nook.biz.node.controller.agent.admin.vo.AdminTruncateLogReqVO;
import com.nook.biz.node.dal.dataobject.agent.AgentTaskDO;
import com.nook.common.web.response.PageResult;

import java.util.List;

/** Admin Agent 管理 service; 拼接 server + runtime + capacity 数据. */
public interface AdminAgentService {

    List<AdminAgentListItemRespVO> list();

    AdminAgentDetailRespVO detail(String serverId);

    /** 派 agent_upgrade task; agent 拉 backend 当前 FS 上的 binary. 返回 taskId. */
    String dispatchUpgrade(String serverId);

    /** 派 truncate_log task; 返回 taskId. */
    String dispatchTruncateLog(String serverId, AdminTruncateLogReqVO req);

    /** Admin 查某 server task 分页 (倒序, 含类型/状态筛选). */
    PageResult<AgentTaskDO> pageTasks(String serverId, AdminAgentTaskPageReqVO reqVO);
}
