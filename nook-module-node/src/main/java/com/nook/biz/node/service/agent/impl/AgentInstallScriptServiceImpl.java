package com.nook.biz.node.service.agent.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.controller.resource.vo.AgentInstallReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.framework.server.script.NookScripts;
import com.nook.biz.node.service.agent.AgentInstallScriptService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import com.nook.framework.ssh.script.ScriptCatalog;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Agent SSH 自动装机实现: 复用 resource_server 存的 SSH 凭据, 表单字段 → 拼 yaml → 写到远端.
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentInstallScriptServiceImpl implements AgentInstallScriptService {

    private final ResourceServerMapper resourceServerMapper;
    private final ResourceServerValidator serverValidator;
    @Resource
    private ScriptCatalog scriptCatalog;

    /** Backend 公网 URL; agent 调下载接口用. application.yml 配 nook.backend.public-url. */
    @Value("${nook.backend.public-url:}")
    private String backendPublicUrl;

    @Override
    // 故意不加 @Transactional: token UPDATE 必须立即 commit, SSH 脚本里 curl 拉 binary 才能用新 token 过 X-Agent-Token 校验
    public void installStreaming(String serverId, AgentInstallReqVO reqVO, Consumer<String> lineSink) {
        ResourceServerDO srv = serverValidator.validateExists(serverId);
        if (StrUtil.isBlank(backendPublicUrl)) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "nook.backend.public-url 未配置, agent 无法回拉 binary");
        }
        // frontline 必须带 xray 三件套
        if ("frontline".equals(reqVO.getRole())) {
            if (StrUtil.isBlank(reqVO.getXrayBin())
                    || reqVO.getXrayApiPort() == null
                    || reqVO.getXrayStatsIntervalSeconds() == null) {
                throw new BusinessException(CommonErrorCode.PARAM_INVALID,
                        "frontline role 必须填 xrayBin / xrayApiPort / xrayStatsIntervalSeconds");
            }
        }

        // 1) 重置 agent_token + 拼 yaml; 旧 agent 心跳立刻失效 (预期, 装新的会重写 config)
        String newToken = generateAgentToken(serverId);
        String configYaml = buildYaml(reqVO, backendPublicUrl, newToken);
        resourceServerMapper.update(null, Wrappers.<ResourceServerDO>lambdaUpdate()
                .set(ResourceServerDO::getAgentToken, newToken)
                .set(ResourceServerDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerDO::getId, serverId));
        lineSink.accept("[nook] agent_token 已重置: " + StrUtil.subPre(newToken, 12) + "…\n");
        lineSink.accept("[nook] role: " + reqVO.getRole() + " / server: " + srv.getName() + "\n");
        lineSink.accept("[nook] 渲染 yaml: " + configYaml.length() + " 字节\n");

        // 2) SSH 装机; 复用 INSTALL scope (长任务, 跟短任务隔离 cache)
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.INSTALL);
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("SERVER_NAME", srv.getName());
        vars.put("ROLE", reqVO.getRole());
        vars.put("BACKEND_URL", backendPublicUrl);
        vars.put("AGENT_TOKEN", newToken);
        vars.put("CONFIG_YAML", configYaml);
        Duration installTimeout = Duration.ofSeconds(session.cred().getInstallTimeoutSeconds());
        scriptCatalog.run(session, NookScripts.NOOK_AGENT_INSTALL, vars, installTimeout, lineSink);

        log.info("[agent-install] 装机完成 serverId={} name={} role={}", serverId, srv.getName(), reqVO.getRole());
    }

    /** 根据表单字段拼 nook-agent 完整 config.yml; 字段全必填, 不做空字段默认. */
    private String buildYaml(AgentInstallReqVO r, String apiUrl, String token) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("# ============================================================================\n");
        sb.append("# nook-").append(r.getRole()).append("-agent 配置 (装机时 backend 渲染; 可在管理端'改配置'热改)\n");
        sb.append("# ----------------------------------------------------------------------------\n\n");
        sb.append("# === backend 通信 ===\n");
        sb.append("backend:\n");
        sb.append("  api_url: ").append(apiUrl).append("\n");
        sb.append("  api_token: ").append(token).append("\n");
        sb.append("  timeout_seconds: ").append(r.getBackendTimeoutSeconds()).append("\n\n");
        sb.append("# === 心跳 ===\n");
        sb.append("heartbeat:\n");
        sb.append("  interval_seconds: ").append(r.getHeartbeatIntervalSeconds()).append("\n\n");
        sb.append("# === NIC 流量 (vnstat) ===\n");
        sb.append("nic:\n");
        sb.append("  interval_seconds: ").append(r.getNicIntervalSeconds()).append("\n");
        sb.append("  interface: ").append(r.getNicInterface()).append("\n\n");
        sb.append("# === 任务轮询 ===\n");
        sb.append("poller:\n");
        sb.append("  interval_seconds: ").append(r.getPollerIntervalSeconds()).append("\n\n");
        if ("frontline".equals(r.getRole())) {
            sb.append("# === Xray 集成 (frontline 专属) ===\n");
            sb.append("xray:\n");
            sb.append("  bin: ").append(r.getXrayBin()).append("\n");
            sb.append("  api_port: ").append(r.getXrayApiPort()).append("\n");
            sb.append("  stats_interval_seconds: ").append(r.getXrayStatsIntervalSeconds()).append("\n\n");
        }
        sb.append("# === 运行时路径 (agent_upgrade 用; 不要手改) ===\n");
        sb.append("runtime:\n");
        sb.append("  bin_path: /home/nook-agent/bin/nook-agent\n");
        return sb.toString();
    }

    /** SHA256(UUID + UUID + serverId) → 64 char hex; 跟 DB CHAR(64) 长度对齐. */
    private String generateAgentToken(String serverId) {
        String raw = UUID.randomUUID() + UUID.randomUUID().toString() + serverId;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }
}
