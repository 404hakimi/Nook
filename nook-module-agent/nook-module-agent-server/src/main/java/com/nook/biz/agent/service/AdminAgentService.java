package com.nook.biz.agent.service;

import com.nook.biz.agent.controller.admin.vo.AdminAgentDetailRespVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentPageReqVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentTaskPageReqVO;
import com.nook.biz.agent.dal.dataobject.AgentRuntimeConfigDO;
import com.nook.biz.agent.dal.dataobject.AgentTaskDO;
import com.nook.biz.node.api.resource.dto.ResourceServerCapacityRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerCredentialRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRuntimeRespDTO;
import com.nook.biz.node.api.xray.dto.XrayNodeRespDTO;
import com.nook.common.web.response.PageResult;

import java.util.Collection;
import java.util.Map;

/**
 * Admin Agent 管理 Service 接口
 *
 * @author nook
 */
public interface AdminAgentService {

    /**
     * Agent 总览分页 (raw entity, 不构建 VO; 关联数据由 Controller 调 loadListAggregates 批量查后由 Convert 拼接)
     *
     * @param reqVO 分页 + 筛选
     * @return server raw 分页
     */
    PageResult<ResourceServerRespDTO> pageServers(AdminAgentPageReqVO reqVO);

    /**
     * 批量加载列表项关联聚合 (credential / runtime / capacity / xray / runtime-config)
     *
     * @param serverIds server id 集合
     * @return 5 个 serverId → 关联 Map 的聚合
     */
    ListAggregates loadListAggregates(Collection<String> serverIds);

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

    /**
     * 列表项关联聚合; Convert 拼接时按 serverId 取值
     */
    record ListAggregates(
            Map<String, ResourceServerCredentialRespDTO> credentialMap,
            Map<String, ResourceServerRuntimeRespDTO> runtimeMap,
            Map<String, ResourceServerCapacityRespDTO> capacityMap,
            Map<String, XrayNodeRespDTO> xrayMap,
            Map<String, AgentRuntimeConfigDO> cfgMap
    ) { }
}
