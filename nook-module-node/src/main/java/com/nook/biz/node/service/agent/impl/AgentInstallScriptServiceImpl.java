package com.nook.biz.node.service.agent.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.biz.node.framework.server.script.NookScripts;
import com.nook.framework.ssh.script.ScriptCatalog;
import com.nook.biz.node.service.agent.AgentInstallScriptService;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.framework.ssh.core.SshSessions;
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

    private final ResourceServerMapper resourceServerMapper;
    private final ResourceServerValidator serverValidator;
    @Resource
    private ScriptCatalog scriptCatalog;

    /** Backend 公网 URL; agent 调下载接口用. application.yml 配 nook.backend.public-url. */
    @Value("${nook.backend.public-url:}")
    private String backendPublicUrl;

    @Override
    // 故意不加 @Transactional: token UPDATE 必须立即 commit, 这样 SSH 脚本里 curl 拉 binary 时
    // backend X-Agent-Token 校验才能读到新值 (否则整个 install 在事务里 SSH 跑了 1-2 min 才 commit, curl 必拒)
    public void installStreaming(String serverId, String role, Consumer<String> lineSink) {
        if (!"frontline".equals(role) && !"landing".equals(role)) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "role 只能是 frontline / landing");
        }
        if (StrUtil.isBlank(backendPublicUrl)) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "nook.backend.public-url 未配置, agent 无法回拉 binary");
        }
        ResourceServerDO srv = serverValidator.validateExists(serverId);

        // 1) 重置 agent_token (旧 agent 心跳立即失效 — 这是预期行为, 装新的会重写 config.yml)
        String newToken = generateAgentToken(serverId);
        resourceServerMapper.update(null, Wrappers.<ResourceServerDO>lambdaUpdate()
                .set(ResourceServerDO::getAgentToken, newToken)
                .set(ResourceServerDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerDO::getId, serverId));
        // 校验完成后立即把新 token 推给前端 (装机过程中可见, 哪怕 SSH 卡住)
        lineSink.accept("[nook] agent_token 已重置: " + StrUtil.subPre(newToken, 12) + "…\n");
        lineSink.accept("[nook] backend public-url: " + backendPublicUrl + "\n");
        lineSink.accept("[nook] role: " + role + " / server: " + srv.getName() + "\n");

        // 2) SSH 装机; 复用 INSTALL scope (长任务, 跟短任务隔离 cache)
        SshSession session = SshSessions.acquire(serverId, SshSessionScope.INSTALL);
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("SERVER_NAME", srv.getName());
        vars.put("ROLE", role);
        vars.put("BACKEND_URL", backendPublicUrl);
        vars.put("AGENT_TOKEN", newToken);
        Duration installTimeout = Duration.ofSeconds(session.cred().getInstallTimeoutSeconds());
        scriptCatalog.run(session, NookScripts.NOOK_AGENT_INSTALL, vars, installTimeout, lineSink);

        log.info("[agent-install] 装机完成 serverId={} name={} role={}", serverId, srv.getName(), role);
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
