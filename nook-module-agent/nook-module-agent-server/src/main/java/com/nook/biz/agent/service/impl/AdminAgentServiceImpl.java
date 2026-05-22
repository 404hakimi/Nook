package com.nook.biz.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.agent.controller.admin.vo.AdminAgentDetailRespVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentListItemRespVO;
import com.nook.biz.agent.controller.admin.vo.AdminAgentTaskPageReqVO;
import com.nook.common.web.response.PageResult;
import com.nook.biz.agent.dal.dataobject.AgentRuntimeConfigDO;
import com.nook.biz.agent.dal.dataobject.AgentTaskDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.agent.dal.mysql.mapper.AgentRuntimeConfigMapper;
import com.nook.biz.agent.dal.mysql.mapper.AgentTaskMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerRuntimeMapper;
import com.nook.biz.agent.api.enums.AgentConfigSyncState;
import com.nook.biz.agent.api.enums.AgentOnlineState;
import com.nook.biz.agent.api.enums.AgentTaskType;
import com.nook.biz.agent.service.AdminAgentService;
import com.nook.biz.agent.service.AgentBinaryResolver;
import com.nook.biz.agent.service.AgentTaskDispatchService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.utils.object.BeanUtils;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminAgentServiceImpl implements AdminAgentService {

    private final ResourceServerMapper resourceServerMapper;
    private final ResourceServerRuntimeMapper resourceServerRuntimeMapper;
    private final ResourceServerValidator serverValidator;
    private final AgentTaskDispatchService agentTaskDispatchService;
    private final AgentBinaryResolver agentBinaryResolver;
    private final AgentRuntimeConfigMapper agentRuntimeConfigMapper;
    private final AgentTaskMapper agentTaskMapper;

    /** Backend 公网 URL; agent 调下载接口用. application.yml 配 nook.backend.public-url. */
    @Value("${nook.backend.public-url:}")
    private String backendPublicUrl;

    @Override
    public List<AdminAgentListItemRespVO> list() {
        List<ResourceServerDO> servers = resourceServerMapper.selectList(
                Wrappers.<ResourceServerDO>lambdaQuery().eq(ResourceServerDO::getDeleted, 0));
        if (CollectionUtils.isAnyEmpty(servers)) return List.of();
        var ids = CollectionUtils.convertSet(servers, ResourceServerDO::getId);
        Map<String, ResourceServerRuntimeDO> runtimeMap = CollectionUtils.convertMap(
                resourceServerRuntimeMapper.selectBatchIds(ids),
                ResourceServerRuntimeDO::getServerId);
        Map<String, AgentRuntimeConfigDO> cfgMap = CollectionUtils.convertMap(
                agentRuntimeConfigMapper.selectBatchIds(ids),
                AgentRuntimeConfigDO::getServerId);
        LocalDateTime now = LocalDateTime.now();
        return servers.stream().map(s -> {
            AdminAgentListItemRespVO vo = new AdminAgentListItemRespVO();
            vo.setServerId(s.getId());
            vo.setServerName(s.getName());
            vo.setHost(s.getHost());
            vo.setLifecycleState(s.getLifecycleState());
            ResourceServerRuntimeDO rt = runtimeMap.get(s.getId());
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
            vo.setConfigSyncState(classifyConfigSync(cfgMap.get(s.getId())).name());
            return vo;
        }).toList();
    }

    /**
     * 算配置同步状态.
     *
     * @param row agent_runtime_config 行 (null 表示从未配过)
     * @return 状态枚举
     */
    private static AgentConfigSyncState classifyConfigSync(AgentRuntimeConfigDO row) {
        if (row == null) return AgentConfigSyncState.NEVER_CONFIGURED;
        String storedMd5 = DigestUtils.md5DigestAsHex(
                (row.getConfigYaml() == null ? "" : row.getConfigYaml()).getBytes(StandardCharsets.UTF_8));
        return storedMd5.equals(row.getAppliedYamlMd5())
                ? AgentConfigSyncState.SYNCED
                : AgentConfigSyncState.PENDING;
    }

    @Override
    public AdminAgentDetailRespVO detail(String serverId) {
        ResourceServerDO s = serverValidator.validateExists(serverId);
        ResourceServerRuntimeDO rt = resourceServerRuntimeMapper.selectById(serverId);
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
        serverValidator.validateExists(serverId);
        if (StrUtil.isBlank(backendPublicUrl)) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "nook.backend.public-url 未配置, agent 无法回拉 binary");
        }
        ResourceServerRuntimeDO rt = resourceServerRuntimeMapper.selectById(serverId);
        String role = AgentBinaryResolver.extractRole(rt == null ? null : rt.getAgentVersion());
        AgentBinaryResolver.AgentBinary bin = agentBinaryResolver.resolve(role, "linux", "amd64");
        String url = backendPublicUrl + "/admin/agent-dist/bin?role=" + role;
        String fullVersion = role + "-" + bin.version();
        String payload = String.format("{\"url\":\"%s\",\"sha256\":\"%s\",\"version\":\"%s\"}",
                escape(url), escape(bin.sha256()), escape(fullVersion));
        return agentTaskDispatchService.dispatch(serverId, AgentTaskType.AGENT_UPGRADE.getCode(), payload);
    }

    @Override
    public PageResult<AgentTaskDO> pageTasks(String serverId, AdminAgentTaskPageReqVO reqVO) {
        serverValidator.validateExists(serverId);
        IPage<AgentTaskDO> page = agentTaskMapper.selectPageByServer(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), serverId, reqVO);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
