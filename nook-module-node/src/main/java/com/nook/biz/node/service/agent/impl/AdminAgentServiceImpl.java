package com.nook.biz.node.service.agent.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.node.controller.agent.admin.vo.AdminAgentDetailRespVO;
import com.nook.biz.node.controller.agent.admin.vo.AdminAgentListItemRespVO;
import com.nook.biz.node.controller.agent.admin.vo.AdminAgentTaskPageReqVO;
import com.nook.biz.node.controller.agent.admin.vo.AdminTruncateLogReqVO;
import com.nook.common.web.response.PageResult;
import com.nook.biz.node.dal.dataobject.agent.AgentRuntimeConfigDO;
import com.nook.biz.node.dal.dataobject.agent.AgentTaskDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerRuntimeDO;
import com.nook.biz.node.dal.mysql.mapper.AgentRuntimeConfigMapper;
import com.nook.biz.node.dal.mysql.mapper.AgentTaskMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerRuntimeMapper;
import com.nook.biz.node.service.agent.AdminAgentService;
import com.nook.biz.node.service.agent.AgentBinaryResolver;
import com.nook.biz.node.service.agent.AgentTaskDispatchService;
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
            if (rt != null) {
                vo.setAgentVersion(rt.getAgentVersion());
                vo.setLastHeartbeatAt(rt.getLastHeartbeatAt());
                vo.setTempUnhealthy(rt.getTempUnhealthy());
                if (rt.getLastHeartbeatAt() != null) {
                    long elapsed = Duration.between(rt.getLastHeartbeatAt(), now).getSeconds();
                    vo.setElapsedSec(elapsed);
                    vo.setOnlineState(classifyOnline(elapsed, rt.getTempUnhealthy()));
                } else {
                    vo.setOnlineState("NEVER");
                }
            } else {
                vo.setOnlineState("NEVER");
            }
            vo.setConfigSyncState(classifyConfigSync(cfgMap.get(s.getId())));
            return vo;
        }).toList();
    }

    /** 配置同步: 没行 → NEVER_CONFIGURED; applied_md5 == stored md5 → SYNCED; 其他 → PENDING. */
    private static String classifyConfigSync(AgentRuntimeConfigDO row) {
        if (row == null) return "NEVER_CONFIGURED";
        String storedMd5 = DigestUtils.md5DigestAsHex(
                (row.getConfigYaml() == null ? "" : row.getConfigYaml()).getBytes(StandardCharsets.UTF_8));
        return storedMd5.equals(row.getAppliedYamlMd5()) ? "SYNCED" : "PENDING";
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
        if (rt != null) {
            vo.setAgentVersion(rt.getAgentVersion());
            vo.setLastAgentSeenIp(rt.getLastAgentSeenIp());
            vo.setLastHeartbeatAt(rt.getLastHeartbeatAt());
            vo.setTempUnhealthy(rt.getTempUnhealthy());
            vo.setConsecutiveMiss(rt.getConsecutiveMiss());
            if (rt.getLastHeartbeatAt() != null) {
                long elapsed = Duration.between(rt.getLastHeartbeatAt(), LocalDateTime.now()).getSeconds();
                vo.setElapsedSec(elapsed);
                vo.setOnlineState(classifyOnline(elapsed, rt.getTempUnhealthy()));
            } else {
                vo.setOnlineState("NEVER");
            }
        } else {
            vo.setOnlineState("NEVER");
        }
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
        return agentTaskDispatchService.dispatch(serverId, "agent_upgrade", payload);
    }

    @Override
    public PageResult<AgentTaskDO> pageTasks(String serverId, AdminAgentTaskPageReqVO reqVO) {
        serverValidator.validateExists(serverId);
        IPage<AgentTaskDO> page = agentTaskMapper.selectPageByServer(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), serverId, reqVO);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    @Override
    public String dispatchTruncateLog(String serverId, AdminTruncateLogReqVO req) {
        serverValidator.validateExists(serverId);
        if (CollectionUtils.isAnyEmpty(req.getPaths())) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "paths 不能为空");
        }
        // 简易 JSON 拼装; 路径不允许双引号 (admin UI 应该禁止)
        StringBuilder sb = new StringBuilder("{\"paths\":[");
        for (int i = 0; i < req.getPaths().size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escape(req.getPaths().get(i))).append('"');
        }
        sb.append("]}");
        return agentTaskDispatchService.dispatch(serverId, "truncate_log", sb.toString());
    }

    /** 心跳距今秒数 → 上线状态分类 (跟 AgentHeartbeatTimeoutJob 阈值对齐). */
    private String classifyOnline(long elapsedSec, Integer tempUnhealthy) {
        if (elapsedSec >= 300) return "OFFLINE";          // ≥ 5min, 真故障
        if (elapsedSec >= 180 || (tempUnhealthy != null && tempUnhealthy == 1)) return "TEMP_UNHEALTHY";
        if (elapsedSec >= 60) return "WARN";
        return "ONLINE";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
