package com.nook.biz.node.service.support;

import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshSession;
import com.nook.framework.ssh.core.SshSessionManager;
import com.nook.framework.ssh.core.SshSessionScope;
import com.nook.biz.resource.api.ResourceServerApi;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 业务 DTO ↔ framework 值对象的边界转换器, 并暴露一站式 acquire(serverId, scope) 取代各 service 私有 acquireSession.
 *
 * @author nook
 */
@Component
public class SessionCredentialMapper {

    @Resource
    private ResourceServerApi resourceServerApi;
    @Resource
    private SshSessionManager sessionManager;

    /**
     * 一站式: 按 serverId 加载凭据 → 边界转 SessionCredential → framework acquire SSH session.
     * 替代各 service 重复的 acquireSession 私有方法.
     *
     * @param serverId resource_server.id
     * @param scope    会话作用域
     * @return SshSession
     */
    public SshSession acquire(String serverId, SshSessionScope scope) {
        SessionCredential credential = convent(resourceServerApi.loadCredential(serverId));
        return sessionManager.acquire(credential, scope);
    }

    /**
     * 业务 DTO → SSH 凭据值对象
     *
     * @param credential 业务侧凭据 DTO
     * @return SessionCredential
     */
    private static SessionCredential convent(ServerCredentialDTO credential) {
        return SessionCredential.builder()
                .serverId(credential.getServerId())
                .sshHost(credential.getSshHost())
                .sshPort(credential.getSshPort())
                .sshUser(credential.getSshUser())
                .sshPassword(credential.getSshPassword())
                .sshTimeoutSeconds(credential.getSshTimeoutSeconds())
                .sshOpTimeoutSeconds(credential.getSshOpTimeoutSeconds())
                .sshUploadTimeoutSeconds(credential.getSshUploadTimeoutSeconds())
                .installTimeoutSeconds(credential.getInstallTimeoutSeconds())
                .build();
    }
}
