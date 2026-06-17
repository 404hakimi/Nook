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
import com.nook.biz.node.api.resource.ResourceServerCredentialApi;
import com.nook.biz.node.api.resource.dto.ResourceServerCredentialRespDTO;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import com.nook.biz.node.api.xray.XrayInstallApi;
import com.nook.biz.node.api.xray.XrayInstallDefaults;
import com.nook.biz.node.api.xray.dto.XrayInstallRespDTO;
import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshSessions;
import com.nook.framework.ssh.script.ScriptCatalog;
import com.nook.framework.web.StreamingEndpointSupport;
import com.nook.framework.web.WebStreamingProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

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
public class AgentInstallScriptServiceImpl implements AgentInstallScriptService {

    /** agent 控制接口固定端口; 后台 call agent 部署用, 装机时 UFW 放行后台 IP. */
    private static final int CONTROL_PORT = 44844;
    /** xray stats 上报间隔; agent-side 轮询率, 装机时统一值. */
    private static final int XRAY_STATS_INTERVAL_SECONDS = 300;
    /** reconcile (对账) 间隔默认值; admin 未填时用. */
    private static final int DEFAULT_RECONCILE_INTERVAL_SECONDS = 300;

    @Resource
    private ResourceServerApi resourceServerApi;
    @Resource
    private ResourceServerCredentialApi resourceServerCredentialApi;
    @Resource
    private XrayInstallApi xrayInstallApi;
    @Resource
    private ScriptCatalog scriptCatalog;
    @Resource
    private AgentInstallValidator agentInstallValidator;
    @Resource
    private AgentProperties agentProperties;
    @Resource
    private StreamingEndpointSupport streamingSupport;
    @Resource
    private WebStreamingProperties webStreamingProperties;

    @Override
    public ResponseBodyEmitter installStream(String sourceId, AgentInstallReqVO reqVO) {
        // 校验角色与装机源存在
        agentInstallValidator.validateRole(reqVO.getRole());
        resourceServerApi.validateExists(sourceId);
        // 按凭据装机超时推流式响应超时
        ResourceServerCredentialRespDTO cred = resourceServerCredentialApi.requireByServerId(sourceId);
        Duration emitterTimeout = Duration.ofSeconds(cred.getInstallTimeoutSeconds())
                .plus(webStreamingProperties.getEmitterBuffer());
        // 流式执行装机
        return streamingSupport.stream("agent-install:" + sourceId, emitterTimeout,
                lineSink -> installStreaming(sourceId, reqVO, lineSink));
    }

    @Override
    public void installStreaming(String sourceId, AgentInstallReqVO reqVO, Consumer<String> lineSink) {
        // 校验角色 / 装机源 / 服务器类型
        agentInstallValidator.validateRole(reqVO.getRole());
        AgentRole role = AgentRole.fromCode(reqVO.getRole());
        ResourceServerRespDTO srv = resourceServerApi.validateExists(sourceId);
        agentInstallValidator.validateServerType(srv, role);
        // 取 SSH 凭据并按角色校验装机前置
        ResourceServerCredentialRespDTO cred = resourceServerCredentialApi.requireByServerId(sourceId);
        if (role == AgentRole.FRONTLINE) {
            // 完整重排: agent 先装, xray 之后由 agent 部署; 这里不再要求 xray 先装
            agentInstallValidator.validateFrontlinePrerequisite(srv);
        } else {
            agentInstallValidator.validateLandingPrerequisite(srv);
        }
        // 渲染 agent 配置
        String token = srv.getAgentToken();
        String configYaml = buildYaml(reqVO, token);
        lineSink.accept("[nook] agent_token: " + StrUtil.subPre(token, 12) + "… (复用 server 入库时签发)\n");
        lineSink.accept("[nook] role: " + reqVO.getRole() + " / server: " + srv.getName() + "\n");
        lineSink.accept("[nook] 渲染 yaml: " + configYaml.length() + " 字节\n");
        // 组 SSH 凭据并执行装机脚本
        SessionCredential sshCred = SessionCredential.builder()
                .serverId(srv.getId())
                .sshHost(srv.getIpAddress())
                .sshPort(cred.getSshPort())
                .sshUser(cred.getSshUser())
                .sshPassword(cred.getSshPassword())
                .sshTimeoutSeconds(reqVO.getSshTimeoutSeconds())
                .sshOpTimeoutSeconds(reqVO.getSshOpTimeoutSeconds())
                .sshUploadTimeoutSeconds(reqVO.getSshUploadTimeoutSeconds())
                .installTimeoutSeconds(reqVO.getInstallTimeoutSeconds())
                .build();
        runInstall(sshCred, srv.getName(), reqVO, token, configYaml, lineSink);

        log.info("[install] role={} sourceId={} name={}", reqVO.getRole(), sourceId, srv.getName());
    }

    /** 共用 SSH 执行段; serverName 都以 SERVER_NAME 变量塞给装机脚本. */
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
    public AgentInstallMetaRespVO getInstallMeta(AgentRole role, String sourceId) {
        AgentInstallMetaRespVO vo = new AgentInstallMetaRespVO();
        vo.setBackendUrl(StrUtil.blankToDefault(agentProperties.getBackendPublicUrl(), ""));
        if (StrUtil.isBlank(sourceId)) {
            return vo;
        }
        ResourceServerRespDTO srv = resourceServerApi.validateExists(sourceId);
        ResourceServerCredentialRespDTO cred = resourceServerCredentialApi.getByServerId(sourceId);
        if (cred != null) {
            vo.setSshTimeoutSeconds(cred.getSshTimeoutSeconds());
            vo.setSshOpTimeoutSeconds(cred.getSshOpTimeoutSeconds());
            vo.setSshUploadTimeoutSeconds(cred.getSshUploadTimeoutSeconds());
            vo.setInstallTimeoutSeconds(cred.getInstallTimeoutSeconds());
        }
        if (role == AgentRole.FRONTLINE) {
            XrayInstallRespDTO xray = xrayInstallApi.getXrayInstall(sourceId);
            if (xray != null) {
                vo.setXrayBin(xray.getXrayBinaryPath());
                vo.setXrayApiPort(xray.getXrayApiPort());
            }
        } else {
            vo.setIpAddress(srv.getIpAddress());
        }
        return vo;
    }

    /** 拼 nook-agent config.yml. 所有值 (URL / 路径 / xray) 都来自 DTO, backend 不兜底. */
    private String buildYaml(AgentInstallReqVO r, String token) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("# nook-").append(r.getRole()).append("-agent 配置 (装机时 backend 渲染; 改配置后需重新部署)\n\n");
        sb.append("backend:\n");
        sb.append("  api_url: ").append(r.getBackendUrl()).append("\n");
        sb.append("  api_token: ").append(token).append("\n");
        sb.append("  timeout_seconds: ").append(r.getBackendTimeoutSeconds()).append("\n\n");
        sb.append("control:\n");
        sb.append("  port: ").append(CONTROL_PORT).append("\n\n");
        sb.append("heartbeat:\n");
        sb.append("  interval_seconds: ").append(r.getHeartbeatIntervalSeconds()).append("\n\n");
        sb.append("nic:\n");
        sb.append("  interval_seconds: ").append(r.getNicIntervalSeconds()).append("\n");
        sb.append("  interface: ").append(r.getNicInterface()).append("\n\n");
        int reconcileInterval = r.getReconcileIntervalSeconds() == null
                ? DEFAULT_RECONCILE_INTERVAL_SECONDS : r.getReconcileIntervalSeconds();
        // 完整重排: 装 agent 时就写固定 xray 段 (路径/端口后端默认); reconcile 据此探, xray 部署好后自动对账
        if (AgentRole.FRONTLINE.matches(r.getRole())) {
            sb.append("xray:\n");
            sb.append("  bin: ").append(XrayInstallDefaults.XRAY_BINARY_PATH).append("\n");
            sb.append("  api_port: ").append(XrayInstallDefaults.API_PORT).append("\n");
            sb.append("  stats_interval_seconds: ").append(XRAY_STATS_INTERVAL_SECONDS).append("\n");
            sb.append("  reconcile_interval_seconds: ").append(reconcileInterval).append("\n\n");
        } else if (AgentRole.LANDING.matches(r.getRole())) {
            // 落地机 tc 限速 reconcile 周期; 复用部署表单的"对账间隔"
            sb.append("landing:\n");
            sb.append("  bandwidth_reconcile_interval_seconds: ").append(reconcileInterval).append("\n\n");
        }
        return sb.toString();
    }
}
