package com.nook.biz.agent.service;

import com.nook.biz.agent.controller.admin.vo.AdminAgentTaskPageReqVO;
import com.nook.biz.agent.dal.dataobject.AgentTaskDO;
import com.nook.common.web.response.PageResult;

/**
 * Admin Agent 管理 Service 接口
 *
 * @author nook
 */
public interface AdminAgentService {

    /**
     * 派发升级任务
     *
     * @param serverId server 编号
     * @return 任务编号
     */
    String dispatchUpgrade(String serverId);

    /**
     * 获得 Agent 任务分页
     *
     * @param serverId server 编号
     * @param reqVO    分页 + 筛选条件
     * @return 任务分页
     */
    PageResult<AgentTaskDO> pageTasks(String serverId, AdminAgentTaskPageReqVO reqVO);
}
