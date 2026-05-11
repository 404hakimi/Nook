package com.nook.biz.node.service.support;

import com.nook.biz.node.resource.entity.ResourceServer;
import com.nook.biz.node.resource.service.ResourceServerService;
import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionManager;
import com.nook.framework.ssh.core.SshSessionScope;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * resource_server 实体 → framework SSH 值对象的边界转换器, 并暴露一站式 acquire(serverId, scope).
 *
 * @author nook
 */
@Component
public class SessionCredentialMapper {

    @Resource
    private ResourceServerService resourceServerService;
    @Resource
    private SshSessionManager sessionManager;

    /** 加载 server 实体 → 转 SessionCredential → framework acquire SSH session. */
    public SshSession acquire(String serverId, SshSessionScope scope) {
        SessionCredential credential = toCredential(resourceServerService.findById(serverId));
        return sessionManager.acquire(credential, scope);
    }

    private static SessionCredential toCredential(ResourceServer e) {
        return SessionCredential.builder()
                .serverId(e.getId())
                .sshHost(e.getHost())
                .sshPort(e.getSshPort())
                .sshUser(e.getSshUser())
                .sshPassword(e.getSshPassword())
                .sshTimeoutSeconds(e.getSshTimeoutSeconds())
                .sshOpTimeoutSeconds(e.getSshOpTimeoutSeconds())
                .sshUploadTimeoutSeconds(e.getSshUploadTimeoutSeconds())
                .installTimeoutSeconds(e.getInstallTimeoutSeconds())
                .build();
    }
}
