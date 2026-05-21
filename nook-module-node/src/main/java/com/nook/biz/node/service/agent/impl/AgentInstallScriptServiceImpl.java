package com.nook.biz.node.service.agent.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.controller.resource.vo.AgentInstallReqVO;
import com.nook.biz.node.dal.dataobject.node.XrayNodeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.dal.mysql.mapper.XrayNodeMapper;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.server.script.NookScripts;
import com.nook.biz.node.service.agent.AgentInstallScriptService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
import com.nook.framework.ssh.script.ScriptCatalog;
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

/** Agent SSH 自动装机: 表单字段拼 yaml, frontline 必须先装 xray. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentInstallScriptServiceImpl implements AgentInstallScriptService {

    private final ResourceServerMapper resourceServerMapper;
    private final ResourceServerValidator serverValidator;
    private final XrayNodeMapper xrayNodeMapper;
    private final ScriptCatalog scriptCatalog;

    /** Backend 公网 URL; agent 装机后回拉 binary / heartbeat. */
    @Value("${nook.backend.public-url:}")
    private String backendPublicUrl;

    /** xray stats 上报间隔; agent-side 轮询率, 装机时统一值, 后续 ConfigEditDialog 可改. */
    private static final int XRAY_STATS_INTERVAL_SECONDS = 300;

    @Override
    // 不加 @Transactional: agent_token UPDATE 要立即 commit, 否则 SSH 脚本里 curl 拉 binary 时 X-Agent-Token 校验拿不到新值
    public void installStreaming(String serverId, AgentInstallReqVO reqVO, Consumer<String> lineSink) {
        ResourceServerDO srv = serverValidator.validateExists(serverId);
        if (StrUtil.isBlank(backendPublicUrl)) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "nook.backend.public-url 未配置, agent 无法回拉 binary");
        }

        XrayNodeDO xrayNode = null;
        if ("frontline".equals(reqVO.getRole())) {
            xrayNode = xrayNodeMapper.selectById(serverId);
            if (xrayNode == null) {
                throw new BusinessException(XrayErrorCode.XRAY_NOT_INSTALLED, srv.getName());
            }
            lineSink.accept("[nook] xray: bin=" + xrayNode.getXrayBinaryPath()
                    + " apiPort=" + xrayNode.getXrayApiPort() + "\n");
        }

        String newToken = generateAgentToken(serverId);
        String configYaml = buildYaml(reqVO, backendPublicUrl, newToken, xrayNode);
        resourceServerMapper.update(null, Wrappers.<ResourceServerDO>lambdaUpdate()
                .set(ResourceServerDO::getAgentToken, newToken)
                .set(ResourceServerDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerDO::getId, serverId));
        lineSink.accept("[nook] agent_token 已重置: " + StrUtil.subPre(newToken, 12) + "…\n");
        lineSink.accept("[nook] role: " + reqVO.getRole() + " / server: " + srv.getName() + "\n");
        lineSink.accept("[nook] 渲染 yaml: " + configYaml.length() + " 字节\n");

        SshSession session = SshSessions.acquire(serverId, SshSessionScope.INSTALL);
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("SERVER_NAME", srv.getName());
        vars.put("ROLE", reqVO.getRole());
        vars.put("BACKEND_URL", backendPublicUrl);
        vars.put("AGENT_TOKEN", newToken);
        vars.put("CONFIG_YAML", configYaml);
        Duration installTimeout = Duration.ofSeconds(session.cred().getInstallTimeoutSeconds());
        scriptCatalog.run(session, NookScripts.NOOK_AGENT_INSTALL, vars, installTimeout, lineSink);

        log.info("[installStreaming] 装机完成 serverId={} name={} role={}",
                serverId, srv.getName(), reqVO.getRole());
    }

    /**
     * 拼 nook-agent config.yml.
     *
     * @param r        表单字段 (各间隔 + nic interface)
     * @param apiUrl   backend 公网 URL
     * @param token    新签发的 agent_token
     * @param xrayNode frontline 时非 null (含 bin / api_port); landing 时 null
     * @return 完整 yaml 字符串
     */
    private String buildYaml(AgentInstallReqVO r, String apiUrl, String token, XrayNodeDO xrayNode) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("# nook-").append(r.getRole()).append("-agent 配置 (装机时 backend 渲染; ConfigEditDialog 可热改)\n\n");
        sb.append("backend:\n");
        sb.append("  api_url: ").append(apiUrl).append("\n");
        sb.append("  api_token: ").append(token).append("\n");
        sb.append("  timeout_seconds: ").append(r.getBackendTimeoutSeconds()).append("\n\n");
        sb.append("heartbeat:\n");
        sb.append("  interval_seconds: ").append(r.getHeartbeatIntervalSeconds()).append("\n\n");
        sb.append("nic:\n");
        sb.append("  interval_seconds: ").append(r.getNicIntervalSeconds()).append("\n");
        sb.append("  interface: ").append(r.getNicInterface()).append("\n\n");
        sb.append("poller:\n");
        sb.append("  interval_seconds: ").append(r.getPollerIntervalSeconds()).append("\n\n");
        if (xrayNode != null) {
            sb.append("xray:\n");
            sb.append("  bin: ").append(xrayNode.getXrayBinaryPath()).append("\n");
            sb.append("  api_port: ").append(xrayNode.getXrayApiPort()).append("\n");
            sb.append("  stats_interval_seconds: ").append(XRAY_STATS_INTERVAL_SECONDS).append("\n\n");
        }
        sb.append("runtime:\n");
        sb.append("  bin_path: /home/nook-agent/bin/nook-agent\n");
        return sb.toString();
    }

    /** SHA256(UUID + UUID + serverId) → 64 char hex; 跟 DB CHAR(64) 长度对齐. */
    private String generateAgentToken(String serverId) {
        String raw = UUID.randomUUID() + UUID.randomUUID().toString() + serverId;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }
}
