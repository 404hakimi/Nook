package com.nook.biz.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.agent.controller.vo.AgentInstallMetaRespVO;
import com.nook.biz.agent.controller.vo.AgentInstallReqVO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.XrayNodeMapper;
import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.node.framework.server.script.NookScripts;
import com.nook.biz.agent.service.AgentInstallScriptService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshSessions;
import com.nook.framework.ssh.script.ScriptCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Agent SSH 自动装机: 表单字段拼 yaml, frontline 必须先装 xray. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentInstallScriptServiceImpl implements AgentInstallScriptService {

    private final ResourceServerValidator serverValidator;
    private final XrayNodeMapper xrayNodeMapper;
    private final ScriptCatalog scriptCatalog;

    /** Backend 公网 URL; agent 装机后回拉 binary / heartbeat. */
    @Value("${nook.backend.public-url:}")
    private String backendPublicUrl;

    /** xray stats 上报间隔; agent-side 轮询率, 装机时统一值, 后续 ConfigEditDialog 可改. */
    private static final int XRAY_STATS_INTERVAL_SECONDS = 300;

    @Override
    public void installStreaming(String serverId, AgentInstallReqVO reqVO, Consumer<String> lineSink) {
        ResourceServerDO srv = serverValidator.validateExists(serverId);
        // agent_token 由 createServer 一次性签发, 装机/重装/升级永远复用; 缺失说明是历史 server 漏签
        if (StrUtil.isBlank(srv.getAgentToken())) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "server " + srv.getName() + " 缺 agent_token; 用 UPDATE resource_server SET agent_token=... 补一个");
        }

        // frontline 装机必须带 xray bin + api_port (前端从 /agent-install-meta 读 xray_node 后回塞);
        // 不再后端重查, 但强制非空校验避免前端漏传
        if (AgentRole.FRONTLINE.getCode().equals(reqVO.getRole())) {
            if (StrUtil.isBlank(reqVO.getXrayBin()) || reqVO.getXrayApiPort() == null) {
                throw new BusinessException(CommonErrorCode.PARAM_INVALID, "frontline 装机必须传 xrayBin + xrayApiPort (server " + srv.getName() + " 可能未装 xray)");
            }
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
                scriptCatalog.run(session, NookScripts.NOOK_AGENT_INSTALL, vars, installTimeout, lineSink));

        log.info("[installStreaming] 装机完成 serverId={} name={} role={}",
                serverId, srv.getName(), reqVO.getRole());
    }

    @Override
    public AgentInstallMetaRespVO getInstallMeta(String role, String serverId) {
        if (!AgentRole.isValid(role)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "role 只能是 frontline / landing");
        }
        // meta 只返 "backend 已知数据" (admin 前端拿去 prefill, 用户可改); 路径默认由前端持有, 此处不下发
        AgentInstallMetaRespVO vo = new AgentInstallMetaRespVO();
        vo.setBackendUrl(StrUtil.blankToDefault(backendPublicUrl, ""));
        if (StrUtil.isNotBlank(serverId)) {
            ResourceServerDO srv = serverValidator.validateExists(serverId);
            // SSH 默认从 resource_server 读
            vo.setSshTimeoutSeconds(srv.getSshTimeoutSeconds());
            vo.setSshOpTimeoutSeconds(srv.getSshOpTimeoutSeconds());
            vo.setSshUploadTimeoutSeconds(srv.getSshUploadTimeoutSeconds());
            vo.setInstallTimeoutSeconds(srv.getInstallTimeoutSeconds());
            // frontline 额外读 xray_node: bin/api_port
            if (AgentRole.FRONTLINE.getCode().equals(role)) {
                XrayNodeDO xrayNode = xrayNodeMapper.selectById(serverId);
                if (xrayNode != null) {
                    vo.setXrayBin(xrayNode.getXrayBinaryPath());
                    vo.setXrayApiPort(xrayNode.getXrayApiPort());
                }
            }
        }
        return vo;
    }

    /**
     * 拼 nook-agent config.yml. 所有值 (URL / 路径 / xray) 都来自 DTO, backend 不兜底.
     */
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
