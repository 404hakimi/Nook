package com.nook.biz.agent.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.agent.api.enums.AgentHostType;
import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.agent.api.enums.AgentTaskType;
import com.nook.biz.agent.controller.admin.vo.AdminAgentDetailRespVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentPageReqVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentTaskPageReqVO;
import com.nook.biz.agent.convert.AdminAgentConvert;
import com.nook.biz.agent.dal.dataobject.AgentRuntimeConfigDO;
import com.nook.biz.agent.dal.dataobject.AgentTaskDO;
import com.nook.biz.agent.dal.mysql.mapper.AgentRuntimeConfigMapper;
import com.nook.biz.agent.dal.mysql.mapper.AgentTaskMapper;
import com.nook.biz.agent.framework.binary.AgentBinaryResolver;
import com.nook.biz.agent.framework.config.AgentProperties;
import com.nook.biz.agent.service.AdminAgentService;
import com.nook.biz.agent.service.AgentTaskDispatchService;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.ResourceServerCapacityApi;
import com.nook.biz.node.api.resource.ResourceServerCredentialApi;
import com.nook.biz.node.api.resource.ResourceServerRuntimeApi;
import com.nook.biz.node.api.resource.dto.ResourceServerPageReqDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRuntimeRespDTO;
import com.nook.biz.node.api.xray.XrayServerApi;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Admin Agent 管理 Service 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class AdminAgentServiceImpl implements AdminAgentService {

    private final ResourceServerApi resourceServerApi;
    private final ResourceServerCredentialApi resourceServerCredentialApi;
    private final ResourceServerRuntimeApi resourceServerRuntimeApi;
    private final ResourceServerCapacityApi resourceServerCapacityApi;
    private final XrayServerApi xrayServerApi;
    private final AgentTaskDispatchService agentTaskDispatchService;
    private final AgentBinaryResolver agentBinaryResolver;
    private final AgentRuntimeConfigMapper agentRuntimeConfigMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final AgentProperties agentProperties;

    @Override
    public PageResult<ResourceServerRespDTO> pageServers(AdminAgentPageReqVO reqVO) {
        ResourceServerPageReqDTO dto = BeanUtils.toBean(reqVO, ResourceServerPageReqDTO.class);
        return resourceServerApi.page(dto);
    }

    @Override
    public ListAggregates loadListAggregates(Collection<String> serverIds) {
        if (CollUtil.isEmpty(serverIds)) {
            return new ListAggregates(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Set<String> ids = Set.copyOf(serverIds);
        return new ListAggregates(
                resourceServerCredentialApi.listByServerIds(ids),
                resourceServerRuntimeApi.listByServerIds(ids),
                resourceServerCapacityApi.listByServerIds(ids),
                xrayServerApi.listByServerIds(ids),
                CollectionUtils.convertMap(
                        agentRuntimeConfigMapper.selectBatchIds(ids),
                        AgentRuntimeConfigDO::getServerId));
    }

    @Override
    public AdminAgentDetailRespVO detail(String serverId) {
        ResourceServerRespDTO s = resourceServerApi.validateExists(serverId);
        ResourceServerRuntimeRespDTO rt = resourceServerRuntimeApi.getByServerId(serverId);
        return AdminAgentConvert.INSTANCE.toDetail(s, rt, LocalDateTime.now());
    }

    @Override
    public String dispatchUpgrade(String serverId) {
        resourceServerApi.validateExists(serverId);
        if (StrUtil.isBlank(agentProperties.getBackendPublicUrl())) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "nook.agent.backend-public-url 未配置, agent 无法回拉 binary");
        }
        ResourceServerRuntimeRespDTO rt = resourceServerRuntimeApi.getByServerId(serverId);
        String role = AgentRole.extractCodeFromAgentVersion(rt == null ? null : rt.getAgentVersion());
        AgentBinaryResolver.AgentBinary bin = agentBinaryResolver.resolve(role, "linux", "amd64");
        String url = agentProperties.getBackendPublicUrl() + "/admin/agent-dist/bin?role=" + role;
        String fullVersion = role + "-" + bin.version();
        String payload = String.format("{\"url\":\"%s\",\"sha256\":\"%s\",\"version\":\"%s\"}",
                escape(url), escape(bin.sha256()), escape(fullVersion));
        return agentTaskDispatchService.dispatch(AgentHostType.SERVER, serverId, AgentTaskType.AGENT_UPGRADE.getCode(), payload);
    }

    @Override
    public PageResult<AgentTaskDO> pageTasks(String serverId, AdminAgentTaskPageReqVO reqVO) {
        resourceServerApi.validateExists(serverId);
        IPage<AgentTaskDO> page = agentTaskMapper.selectPageByHost(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()),
                AgentHostType.SERVER.code(), serverId, reqVO);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
