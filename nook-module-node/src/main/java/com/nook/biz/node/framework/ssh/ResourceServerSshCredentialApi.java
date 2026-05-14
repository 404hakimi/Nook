package com.nook.biz.node.framework.ssh;

import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshCredentialApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * SSH 凭据 SPI 业务侧实现: 用 serverId 查 resource_server 后转 framework 凭据.
 *
 * @author nook
 */
@Component
public class ResourceServerSshCredentialApi implements SshCredentialApi {

    @Resource
    private ResourceServerValidator serverValidator;

    @Override
    public SessionCredential load(String serverId) {
        ResourceServerDO resourceServer = serverValidator.validateExists(serverId);
        return SessionCredential.builder()
                .serverId(resourceServer.getId())
                .sshHost(resourceServer.getHost())
                .sshPort(resourceServer.getSshPort())
                .sshUser(resourceServer.getSshUser())
                .sshPassword(resourceServer.getSshPassword())
                .sshTimeoutSeconds(resourceServer.getSshTimeoutSeconds())
                .sshOpTimeoutSeconds(resourceServer.getSshOpTimeoutSeconds())
                .sshUploadTimeoutSeconds(resourceServer.getSshUploadTimeoutSeconds())
                .installTimeoutSeconds(resourceServer.getInstallTimeoutSeconds())
                .build();
    }
}
