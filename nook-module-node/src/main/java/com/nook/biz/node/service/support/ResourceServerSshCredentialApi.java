package com.nook.biz.node.service.support;

import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.validator.ResourceServerValidator;
import com.nook.framework.ssh.core.SessionCredential;
import com.nook.framework.ssh.core.SshCredentialApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * {@link SshCredentialApi} 业务侧实现: 把 serverId 解析为 {@link ResourceServerDO} 再转 framework 凭据.
 *
 * <p>由 nook-spring-boot-starter-ssh 的 {@code SshAutoConfiguration} 通过 @Autowired 注入到
 * {@link com.nook.framework.ssh.core.SshSessions} 静态 facade.
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
