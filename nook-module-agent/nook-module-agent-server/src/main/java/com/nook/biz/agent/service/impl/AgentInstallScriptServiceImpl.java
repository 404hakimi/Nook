package com.nook.biz.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.agent.api.enums.AgentHostType;
import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.agent.controller.vo.AgentInstallMetaRespVO;
import com.nook.biz.agent.controller.vo.AgentInstallReqVO;
import com.nook.biz.agent.framework.config.AgentProperties;
import com.nook.biz.agent.framework.script.AgentScripts;
import com.nook.biz.agent.service.AgentInstallScriptService;
import com.nook.biz.agent.validator.AgentInstallValidator;
import com.nook.biz.node.api.resource.ResourceIpPoolApi;
import com.nook.biz.node.api.resource.ResourceIpPoolCredentialApi;
import com.nook.biz.node.api.resource.ResourceServerApi;
import com.nook.biz.node.api.resource.ResourceServerCredentialApi;
import com.nook.biz.node.api.resource.dto.ResourceIpPoolCredentialRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceIpPoolRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerCredentialRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.api.xray.XrayServerApi;
import com.nook.biz.node.api.xray.dto.XrayServerRespDTO;
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
    private final ResourceServerCredentialApi resourceServerCredentialApi;
    private final ResourceIpPoolApi resourceIpPoolApi;
    private final ResourceIpPoolCredentialApi resourceIpPoolCredentialApi;
    private final XrayServerApi xrayServerApi;
    private final ScriptCatalog scriptCatalog;
    private final AgentInstallValidator agentInstallValidator;
    private final AgentProperties agentProperties;

    @Override
    public void installStreaming(String hostId, AgentInstallReqVO reqVO, Consumer<String> lineSink) {
        AgentHostType hostType = reqVO.getHostType() != null ? reqVO.getHostType() : AgentHostType.SERVER;
        agentInstallValidator.validateRoleHostMatch(reqVO.getRole(), hostType);
        if (hostType == AgentHostType.SERVER) {
            installFrontline(hostId, reqVO, lineSink);
        } else {
            installLanding(hostId, reqVO, lineSink);
        }
    }

    /** Frontline (resource_server): xray 必填 + 复用 server 凭据. */
    private void installFrontline(String serverId, AgentInstallReqVO reqVO, Consumer<String> lineSink) {
        ResourceServerRespDTO srv = resourceServerApi.validateExists(serverId);
        ResourceServerCredentialRespDTO cred = resourceServerCredentialApi.requireByServerId(serverId);
        agentInstallValidator.validateFrontlinePrerequisite(srv, reqVO);
        lineSink.accept("[nook] xray: bin=" + reqVO.getXrayBin() + " apiPort=" + reqVO.getXrayApiPort() + "\n");

        String token = srv.getAgentToken();
        String configYaml = buildYaml(reqVO, token);
        lineSink.accept("[nook] agent_token: " + StrUtil.subPre(token, 12) + "… (复用 server 入库时签发)\n");
        lineSink.accept("[nook] role: " + reqVO.getRole() + " / server: " + srv.getName() + "\n");
        lineSink.accept("[nook] 渲染 yaml: " + configYaml.length() + " 字节\n");

        SessionCredential sshCred = SessionCredential.builder()
                .serverId(srv.getId())
                .sshHost(cred.getHost())
                .sshPort(cred.getSshPort())
                .sshUser(cred.getSshUser())
                .sshPassword(cred.getSshPassword())
                .sshTimeoutSeconds(reqVO.getSshTimeoutSeconds())
                .sshOpTimeoutSeconds(reqVO.getSshOpTimeoutSeconds())
                .sshUploadTimeoutSeconds(reqVO.getSshUploadTimeoutSeconds())
                .installTimeoutSeconds(reqVO.getInstallTimeoutSeconds())
                .build();
        runInstall(sshCred, srv.getName(), reqVO, token, configYaml, lineSink);

        log.info("[installFrontline] 装机完成 serverId={} name={}", serverId, srv.getName());
    }

    /** Landing (resource_ip_pool): xray 字段忽略; ssh 凭据走 ip_pool_credential, host 留空时用 ip_address 兜底. */
    private void installLanding(String ipId, AgentInstallReqVO reqVO, Consumer<String> lineSink) {
        ResourceIpPoolRespDTO ip = resourceIpPoolApi.validateExists(ipId);
        ResourceIpPoolCredentialRespDTO cred = resourceIpPoolCredentialApi.requireByIpId(ipId);
        agentInstallValidator.validateLandingPrerequisite(ip);

        String token = ip.getAgentToken();
        String configYaml = buildYaml(reqVO, token);
        String displayName = StrUtil.isNotBlank(ip.getRemark()) ? ip.getRemark() : ip.getIpAddress();
        lineSink.accept("[nook] agent_token: " + StrUtil.subPre(token, 12) + "… (复用 ip_pool 入库时签发)\n");
        lineSink.accept("[nook] role: " + reqVO.getRole() + " / ip_pool: " + ip.getIpAddress() + "\n");
        lineSink.accept("[nook] 渲染 yaml: " + configYaml.length() + " 字节\n");

        SessionCredential sshCred = SessionCredential.builder()
                .serverId(ip.getId())
                .sshHost(StrUtil.blankToDefault(cred.getSshHost(), ip.getIpAddress()))
                .sshPort(cred.getSshPort() != null ? cred.getSshPort() : 22)
                .sshUser(StrUtil.blankToDefault(cred.getSshUser(), "root"))
                .sshPassword(cred.getSshPassword())
                .sshTimeoutSeconds(reqVO.getSshTimeoutSeconds())
                .sshOpTimeoutSeconds(reqVO.getSshOpTimeoutSeconds())
                .sshUploadTimeoutSeconds(reqVO.getSshUploadTimeoutSeconds())
                .installTimeoutSeconds(reqVO.getInstallTimeoutSeconds())
                .build();
        runInstall(sshCred, displayName, reqVO, token, configYaml, lineSink);

        log.info("[installLanding] 装机完成 ipId={} ipAddress={}", ipId, ip.getIpAddress());
    }

    /** 共用 SSH 执行段; serverName / ip_address 都以 SERVER_NAME 变量塞给装机脚本. */
    private void runInstall(SessionCredential sshCred, String displayName, AgentInstallReqVO reqVO,
                            String token, String configYaml, Consumer<String> lineSink) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("SERVER_NAME", displayName);
        vars.put("ROLE", reqVO.getRole());
        vars.put("BACKEND_URL", reqVO.getBackendUrl());
        vars.put("AGENT_TOKEN", token);
        vars.put("CONFIG_YAML", configYaml);
        vars.put("NOOK_HOME", reqVO.getNookHome());
        vars.put("BIN_PATH", reqVO.getBinPath());
        vars.put("CONFIG_PATH", reqVO.getConfigPath());
        vars.put("SYSTEMD_UNIT_PATH", reqVO.getSystemdUnitPath());
        Duration installTimeout = Duration.ofSeconds(reqVO.getInstallTimeoutSeconds());
        SshSessions.runAdHocVoid(sshCred, session ->
                scriptCatalog.run(session, AgentScripts.NOOK_AGENT_INSTALL, vars, installTimeout, lineSink));
    }

    @Override
    public AgentInstallMetaRespVO getInstallMeta(String role, AgentHostType hostType, String hostId) {
        agentInstallValidator.validateRole(role);
        AgentHostType resolvedHost = hostType != null ? hostType
                : (AgentRole.LANDING.getCode().equals(role) ? AgentHostType.IP_POOL : AgentHostType.SERVER);
        AgentInstallMetaRespVO vo = new AgentInstallMetaRespVO();
        vo.setBackendUrl(StrUtil.blankToDefault(agentProperties.getBackendPublicUrl(), ""));
        if (StrUtil.isBlank(hostId)) {
            return vo;
        }
        if (resolvedHost == AgentHostType.SERVER) {
            fillServerMeta(role, hostId, vo);
        } else {
            fillIpPoolMeta(hostId, vo);
        }
        return vo;
    }

    /** Frontline + 选了 server: SSH 默认 + (frontline) xray 信息. */
    private void fillServerMeta(String role, String serverId, AgentInstallMetaRespVO vo) {
        resourceServerApi.validateExists(serverId);
        ResourceServerCredentialRespDTO cred = resourceServerCredentialApi.getByServerId(serverId);
        if (cred != null) {
            vo.setSshTimeoutSeconds(cred.getSshTimeoutSeconds());
            vo.setSshOpTimeoutSeconds(cred.getSshOpTimeoutSeconds());
            vo.setSshUploadTimeoutSeconds(cred.getSshUploadTimeoutSeconds());
            vo.setInstallTimeoutSeconds(cred.getInstallTimeoutSeconds());
        }
        if (AgentRole.FRONTLINE.getCode().equals(role)) {
            XrayServerRespDTO xray = xrayServerApi.getByServerId(serverId);
            if (xray != null) {
                vo.setXrayBin(xray.getXrayBinaryPath());
                vo.setXrayApiPort(xray.getXrayApiPort());
            }
        }
    }

    /** Landing + 选了 ipId: 只回 ip_address (admin 展示用); SSH timeouts 由前端写死 default. */
    private void fillIpPoolMeta(String ipId, AgentInstallMetaRespVO vo) {
        ResourceIpPoolRespDTO ip = resourceIpPoolApi.validateExists(ipId);
        vo.setIpAddress(ip.getIpAddress());
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
