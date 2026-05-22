package com.nook.biz.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.agent.controller.vo.AgentInstallMetaRespVO;
import com.nook.biz.agent.controller.vo.AgentInstallReqVO;
import com.nook.biz.agent.framework.config.AgentProperties;
import com.nook.biz.agent.framework.script.AgentScripts;
import com.nook.biz.agent.service.AgentInstallScriptService;
import com.nook.biz.agent.validator.AgentInstallValidator;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.api.xray.XrayNodeApi;
import com.nook.biz.node.api.xray.dto.XrayNodeRespDTO;
import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshSessions;
import com.nook.framework.ssh.script.ScriptCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Agent SSH 自动装机 Service 实现类
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentInstallScriptServiceImpl implements AgentInstallScriptService {

    /** xray stats 上报间隔; agent-side 轮询率, 装机时统一值, 后续 ConfigEditDialog 可改. */
    private static final int XRAY_STATS_INTERVAL_SECONDS = 300;

    private final ResourceServerApi resourceServerApi;
    private final XrayNodeApi xrayNodeApi;
    private final ScriptCatalog scriptCatalog;
    private final AgentInstallValidator agentInstallValidator;
    private final AgentProperties agentProperties;

    @Override
    public void installStreaming(String serverId, AgentInstallReqVO reqVO, Consumer<String> lineSink) {
        ResourceServerRespDTO srv = resourceServerApi.validateExists(serverId);
        agentInstallValidator.validateInstallPrerequisite(srv, reqVO);
        if (AgentRole.FRONTLINE.getCode().equals(reqVO.getRole())) {
            lineSink.accept("[nook] xray: bin=" + reqVO.getXrayBin() + " apiPort=" + reqVO.getXrayApiPort() + "\n");
        }

        String token = srv.getAgentToken();
        String configYaml = buildYaml(reqVO, token);
        lineSink.accept("[nook] agent_token: " + StrUtil.subPre(token, 12) + "… (复用 server 入库时签发)\n");
        lineSink.accept("[nook] role: " + reqVO.getRole() + " / server: " + srv.getName() + "\n");
        lineSink.accept("[nook] 渲染 yaml: " + configYaml.length() + " 字节\n");

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("SERVER_NAME", srv.getName());
        vars.put("ROLE", reqVO.getRole());
        vars.put("BACKEND_URL", reqVO.getBackendUrl());
        vars.put("AGENT_TOKEN", token);
        vars.put("CONFIG_YAML", configYaml);
        vars.put("NOOK_HOME", reqVO.getNookHome());
        vars.put("BIN_PATH", reqVO.getBinPath());
        vars.put("CONFIG_PATH", reqVO.getConfigPath());
        vars.put("SYSTEMD_UNIT_PATH", reqVO.getSystemdUnitPath());
        // 用 DTO 里的 SSH timeouts 覆盖 server 默认值 (一次性, 不回写表); ad-hoc session 跑完即关
        SessionCredential cred = SessionCredential.builder()
                .serverId(srv.getId())
                .sshHost(srv.getHost())
                .sshPort(srv.getSshPort())
                .sshUser(srv.getSshUser())
                .sshPassword(srv.getSshPassword())
                .sshTimeoutSeconds(reqVO.getSshTimeoutSeconds())
                .sshOpTimeoutSeconds(reqVO.getSshOpTimeoutSeconds())
                .sshUploadTimeoutSeconds(reqVO.getSshUploadTimeoutSeconds())
                .installTimeoutSeconds(reqVO.getInstallTimeoutSeconds())
                .build();
        Duration installTimeout = Duration.ofSeconds(reqVO.getInstallTimeoutSeconds());
        SshSessions.runAdHocVoid(cred, session ->
                scriptCatalog.run(session, AgentScripts.NOOK_AGENT_INSTALL, vars, installTimeout, lineSink));

        log.info("[installStreaming] 装机完成 serverId={} name={} role={}",
                serverId, srv.getName(), reqVO.getRole());
    }

    @Override
    public AgentInstallMetaRespVO getInstallMeta(String role, String serverId) {
        agentInstallValidator.validateRole(role);
        // meta 只返 "backend 已知数据" (admin 前端拿去 prefill, 用户可改); 路径默认由前端持有, 此处不下发
        AgentInstallMetaRespVO vo = new AgentInstallMetaRespVO();
        vo.setBackendUrl(StrUtil.blankToDefault(agentProperties.getBackendPublicUrl(), ""));
        if (StrUtil.isNotBlank(serverId)) {
            ResourceServerRespDTO srv = resourceServerApi.validateExists(serverId);
            // SSH 默认从 resource_server 读
            vo.setSshTimeoutSeconds(srv.getSshTimeoutSeconds());
            vo.setSshOpTimeoutSeconds(srv.getSshOpTimeoutSeconds());
            vo.setSshUploadTimeoutSeconds(srv.getSshUploadTimeoutSeconds());
            vo.setInstallTimeoutSeconds(srv.getInstallTimeoutSeconds());
            // frontline 额外读 xray_node: bin/api_port
            if (AgentRole.FRONTLINE.getCode().equals(role)) {
                XrayNodeRespDTO xrayNode = xrayNodeApi.getByServerId(serverId);
                if (xrayNode != null) {
                    vo.setXrayBin(xrayNode.getXrayBinaryPath());
                    vo.setXrayApiPort(xrayNode.getXrayApiPort());
                }
            }
        }
        return vo;
    }

    /** 拼 nook-agent config.yml. 所有值 (URL / 路径 / xray) 都来自 DTO, backend 不兜底. */
    private String buildYaml(AgentInstallReqVO r, String token) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("# nook-").append(r.getRole()).append("-agent 配置 (装机时 backend 渲染; ConfigEditDialog 可热改)\n\n");
        sb.append("backend:\n");
        sb.append("  api_url: ").append(r.getBackendUrl()).append("\n");
        sb.append("  api_token: ").append(token).append("\n");
        sb.append("  timeout_seconds: ").append(r.getBackendTimeoutSeconds()).append("\n\n");
        sb.append("heartbeat:\n");
        sb.append("  interval_seconds: ").append(r.getHeartbeatIntervalSeconds()).append("\n\n");
        sb.append("nic:\n");
        sb.append("  interval_seconds: ").append(r.getNicIntervalSeconds()).append("\n");
        sb.append("  interface: ").append(r.getNicInterface()).append("\n\n");
        sb.append("poller:\n");
        sb.append("  interval_seconds: ").append(r.getPollerIntervalSeconds()).append("\n\n");
        if (AgentRole.FRONTLINE.getCode().equals(r.getRole())) {
            sb.append("xray:\n");
            sb.append("  bin: ").append(r.getXrayBin()).append("\n");
            sb.append("  api_port: ").append(r.getXrayApiPort()).append("\n");
            sb.append("  stats_interval_seconds: ").append(XRAY_STATS_INTERVAL_SECONDS).append("\n\n");
        }
        sb.append("runtime:\n");
        sb.append("  bin_path: ").append(r.getBinPath()).append("\n");
        return sb.toString();
    }
}
