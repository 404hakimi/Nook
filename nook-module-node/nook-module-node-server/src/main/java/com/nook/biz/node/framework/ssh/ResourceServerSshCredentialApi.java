package com.nook.biz.node.framework.ssh;

import com.nook.biz.node.dal.dataobject.resource.ResourceServerCredentialDO;
import com.nook.biz.node.validator.ResourceServerCredentialValidator;
import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshCredentialApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * SSH 凭据 SPI 业务侧实现: 用 serverId 查 resource_server_credential 后转 framework 凭据.
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class ResourceServerSshCredentialApi implements SshCredentialApi {

    private final ResourceServerCredentialValidator credentialValidator;

    @Override
    public SessionCredential load(String serverId) {
        ResourceServerCredentialDO cred = credentialValidator.validateExists(serverId);
        return SessionCredential.builder()
                .serverId(cred.getServerId())
                .sshHost(cred.getHost())
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
