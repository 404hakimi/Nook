package com.nook.biz.node.framework.ssh;

import com.nook.biz.node.entity.ResourceServerCredentialDO;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.validator.ResourceServerCredentialValidator;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshCredentialApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * SSH 凭据 SPI 业务侧实现: 用 serverId 查 resource_server (host) + resource_server_credential (auth) 后转 framework 凭据.
 *
 * @author nook
 */
@Component
public class ResourceServerSshCredentialApi implements SshCredentialApi {

    @Resource
    private ResourceServerCredentialValidator resourceServerCredentialValidator;
    @Resource
    private ResourceServerValidator resourceServerValidator;

    @Override
    public SessionCredential load(String serverId) {
        ResourceServerDO server = resourceServerValidator.validateExists(serverId);
        ResourceServerCredentialDO cred = resourceServerCredentialValidator.validateExists(serverId);
        return SessionCredential.builder()
                .serverId(cred.getServerId())
                .sshHost(server.getIpAddress())
                .sshPort(cred.getSshPort())
                .sshUser(cred.getSshUser())
                .sshPassword(cred.getSshPassword())
                .sshTimeoutSeconds(cred.getSshTimeoutSeconds())
                .sshOpTimeoutSeconds(cred.getSshOpTimeoutSeconds())
                .sshUploadTimeoutSeconds(cred.getSshUploadTimeoutSeconds())
                .installTimeoutSeconds(cred.getInstallTimeoutSeconds())
                .build();
    }
}
