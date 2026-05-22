package com.nook.biz.agent.service;

import com.nook.biz.agent.controller.admin.vo.AdminAgentDetailRespVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentListItemRespVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentPageReqVO;
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
     * Agent 总览分页 (resource_server + runtime + capacity + config 同步状态拼接).
     *
     * @param reqVO 分页 + 筛选
     * @return 列表项分页
     */
    PageResult<AdminAgentListItemRespVO> page(AdminAgentPageReqVO reqVO);

    /**
     * 获得 Agent 详情
     *
     * @param serverId server 编号
     * @return Agent 详情
     */
    AdminAgentDetailRespVO detail(String serverId);

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
