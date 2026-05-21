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
 * Agent SSH 自动装机实现: 复用 resource_server 存的 SSH 凭据, SSH 到目标机跑 install/nook-agent.sh.tmpl.
 *
 * @author nook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentInstallScriptServiceImpl implements AgentInstallScriptService {

    /** configYaml 里这个 sentinel 会被替换成新生成的 agent_token. */
    private static final String AGENT_TOKEN_PLACEHOLDER = "{{AGENT_TOKEN}}";

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
        if (!reqVO.getConfigYaml().contains(AGENT_TOKEN_PLACEHOLDER)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID,
                    "configYaml 必须包含 " + AGENT_TOKEN_PLACEHOLDER + " 占位符 (api_token 行)");
        }

        // 1) 重置 agent_token + 拼进 yaml; 旧 agent 心跳立刻失效 (预期, 装新的会重写 config)
        String newToken = generateAgentToken(serverId);
        String renderedYaml = reqVO.getConfigYaml().replace(AGENT_TOKEN_PLACEHOLDER, newToken);
        resourceServerMapper.update(null, Wrappers.<ResourceServerDO>lambdaUpdate()
                .set(ResourceServerDO::getAgentToken, newToken)
                .set(ResourceServerDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerDO::getId, serverId));
        lineSink.accept("[nook] agent_token 已重置: " + StrUtil.subPre(newToken, 12) + "…\n");
        lineSink.accept("[nook] role: " + reqVO.getRole() + " / server: " + srv.getName() + "\n");
        lineSink.accept("[nook] yaml 大小: " + renderedYaml.length() + " 字节\n");

        // 2) SSH 装机; 复用 INSTALL scope (长任务, 跟短任务隔离 cache)
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.INSTALL);
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("SERVER_NAME", srv.getName());
        vars.put("ROLE", reqVO.getRole());
        vars.put("BACKEND_URL", backendPublicUrl);
        vars.put("AGENT_TOKEN", newToken);
        vars.put("CONFIG_YAML", renderedYaml);
        Duration installTimeout = Duration.ofSeconds(session.cred().getInstallTimeoutSeconds());
        scriptCatalog.run(session, NookScripts.NOOK_AGENT_INSTALL, vars, installTimeout, lineSink);

        log.info("[agent-install] 装机完成 serverId={} name={} role={}", serverId, srv.getName(), reqVO.getRole());
    }

    @Override
    public String defaultConfigYaml(String role) {
        if (!"frontline".equals(role) && !"landing".equals(role)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "role 只能是 frontline / landing");
        }
        String apiUrl = StrUtil.blankToDefault(backendPublicUrl, "http://CHANGE-ME-BACKEND-URL");
        String head = """
                # ============================================================================
                # nook-%s-agent 配置 (装机时 dialog 编辑 → SSH 写入远端)
                # api_token 占位符 {{AGENT_TOKEN}} 由 backend 装机时填; 不要手动改
                # ----------------------------------------------------------------------------

                # === backend 通信 ===
                backend:
                  api_url: %s
                  api_token: {{AGENT_TOKEN}}
                  timeout_seconds: 30

                # === 心跳 (backend 60s 无心跳 → WARN, 300s → OFFLINE) ===
                heartbeat:
                  interval_seconds: 60

                # === NIC 流量 (vnstat) ===
                nic:
                  interval_seconds: 300
                  # auto = agent 用默认路由出口网卡; 多网卡填 eth0/ens5
                  interface: auto

                # === 任务轮询 ===
                poller:
                  interval_seconds: 30
                """.formatted(role, apiUrl);
        String roleBlock = "frontline".equals(role) ? """

                # === Xray 集成 (frontline 专属) ===
                # agent 启动自检 bin 路径是否存在, 在则挂 xray collector
                xray:
                  bin: /usr/local/bin/xray
                  api_port: 10085
                  stats_interval_seconds: 300
                """ : """

                # === Socks5 集成 (landing 专属; 当前空壳) ===
                """;
        String tail = """

                # === 运行时路径 (agent_upgrade 用; 不要手改) ===
                runtime:
                  bin_path: /home/nook-agent/bin/nook-agent
                """;
        return head + roleBlock + tail;
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
