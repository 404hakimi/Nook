package com.nook.biz.agent.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.agent.api.enums.AgentConfigSyncState;
import com.nook.biz.agent.api.enums.AgentOnlineState;
import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.agent.api.enums.AgentTaskType;
import com.nook.biz.agent.controller.admin.vo.AdminAgentDetailRespVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentListItemRespVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentTaskPageReqVO;
import com.nook.biz.agent.convert.AgentRuntimeConfigConvert;
import com.nook.biz.agent.dal.dataobject.AgentRuntimeConfigDO;
import com.nook.biz.agent.dal.dataobject.AgentTaskDO;
import com.nook.biz.agent.dal.mysql.mapper.AgentRuntimeConfigMapper;
import com.nook.biz.agent.dal.mysql.mapper.AgentTaskMapper;
import com.nook.biz.agent.framework.config.AgentProperties;
import com.nook.biz.agent.service.AdminAgentService;
import com.nook.biz.agent.framework.binary.AgentBinaryResolver;
import com.nook.biz.agent.service.AgentTaskDispatchService;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.ResourceServerCapacityApi;
import com.nook.biz.node.api.resource.ResourceServerRuntimeApi;
import com.nook.biz.node.api.resource.dto.ResourceServerCapacityRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRuntimeRespDTO;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    private final ResourceServerRuntimeApi resourceServerRuntimeApi;
    private final ResourceServerCapacityApi resourceServerCapacityApi;
    private final AgentTaskDispatchService agentTaskDispatchService;
    private final AgentBinaryResolver agentBinaryResolver;
    private final AgentRuntimeConfigMapper agentRuntimeConfigMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final AgentProperties agentProperties;

    @Override
    public List<AdminAgentListItemRespVO> list() {
        List<ResourceServerRespDTO> servers = resourceServerApi.listAll();
        if (CollUtil.isEmpty(servers)) return List.of();
        Set<String> ids = CollectionUtils.convertSet(servers, ResourceServerRespDTO::getId);
        Map<String, ResourceServerRuntimeRespDTO> runtimeMap = resourceServerRuntimeApi.listByServerIds(ids);
        Map<String, ResourceServerCapacityRespDTO> capacityMap = resourceServerCapacityApi.listByServerIds(ids);
        Map<String, AgentRuntimeConfigDO> cfgMap = CollectionUtils.convertMap(
                agentRuntimeConfigMapper.selectBatchIds(ids),
                AgentRuntimeConfigDO::getServerId);
        LocalDateTime now = LocalDateTime.now();
        List<AdminAgentListItemRespVO> result = new ArrayList<>(servers.size());
        for (ResourceServerRespDTO s : servers) {
            result.add(buildListItem(s, runtimeMap.get(s.getId()), capacityMap.get(s.getId()),
                    cfgMap.get(s.getId()), now));
        }
        return result;
    }

    /** 单行列表项: 拼 server + runtime + 配置同步 + NIC 流量容量. */
    private static AdminAgentListItemRespVO buildListItem(ResourceServerRespDTO s,
                                                          ResourceServerRuntimeRespDTO rt,
                                                          ResourceServerCapacityRespDTO cap,
                                                          AgentRuntimeConfigDO cfg,
                                                          LocalDateTime now) {
        AdminAgentListItemRespVO vo = new AdminAgentListItemRespVO();
        vo.setServerId(s.getId());
        vo.setServerName(s.getName());
        vo.setHost(s.getHost());
        vo.setLifecycleState(s.getLifecycleState());
        Long elapsedSec = null;
        Integer tempUnhealthy = null;
        if (rt != null) {
            vo.setAgentVersion(rt.getAgentVersion());
            vo.setLastHeartbeatAt(rt.getLastHeartbeatAt());
            vo.setTempUnhealthy(rt.getTempUnhealthy());
            tempUnhealthy = rt.getTempUnhealthy();
            if (rt.getLastHeartbeatAt() != null) {
                elapsedSec = Duration.between(rt.getLastHeartbeatAt(), now).getSeconds();
                vo.setElapsedSec(elapsedSec);
            }
        }
        vo.setOnlineState(AgentOnlineState.classify(elapsedSec, tempUnhealthy).name());
        AgentConfigSyncState syncState = AgentRuntimeConfigConvert.INSTANCE.classifySyncState(cfg);
        vo.setConfigSyncState(syncState.name());
        if (cap != null) {
            vo.setMonthlyTrafficGb(cap.getMonthlyTrafficGb());
            vo.setUsedTrafficBytes(cap.getUsedTrafficBytes());
            vo.setThrottleState(cap.getThrottleState());
        }
        return vo;
    }

    @Override
    public AdminAgentDetailRespVO detail(String serverId) {
        ResourceServerRespDTO s = resourceServerApi.validateExists(serverId);
        ResourceServerRuntimeRespDTO rt = resourceServerRuntimeApi.getByServerId(serverId);
        AdminAgentDetailRespVO vo = BeanUtils.toBean(s, AdminAgentDetailRespVO.class);
        vo.setServerId(s.getId());
        vo.setLifecycleState(s.getLifecycleState());
        if (StrUtil.isNotBlank(s.getAgentToken()) && s.getAgentToken().length() >= 8) {
            vo.setAgentTokenSuffix("..." + s.getAgentToken().substring(s.getAgentToken().length() - 8));
        }
        Long elapsedSec = null;
        Integer tempUnhealthy = null;
        if (rt != null) {
            vo.setAgentVersion(rt.getAgentVersion());
            vo.setLastAgentSeenIp(rt.getLastAgentSeenIp());
            vo.setLastHeartbeatAt(rt.getLastHeartbeatAt());
            vo.setTempUnhealthy(rt.getTempUnhealthy());
            vo.setConsecutiveMiss(rt.getConsecutiveMiss());
            tempUnhealthy = rt.getTempUnhealthy();
            if (rt.getLastHeartbeatAt() != null) {
                elapsedSec = Duration.between(rt.getLastHeartbeatAt(), LocalDateTime.now()).getSeconds();
                vo.setElapsedSec(elapsedSec);
            }
        }
        vo.setOnlineState(AgentOnlineState.classify(elapsedSec, tempUnhealthy).name());
        return vo;
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
        return agentTaskDispatchService.dispatch(serverId, AgentTaskType.AGENT_UPGRADE.getCode(), payload);
    }

    @Override
    public PageResult<AgentTaskDO> pageTasks(String serverId, AdminAgentTaskPageReqVO reqVO) {
        resourceServerApi.validateExists(serverId);
        IPage<AgentTaskDO> page = agentTaskMapper.selectPageByServer(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), serverId, reqVO);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
