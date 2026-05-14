package com.nook.biz.node.service.support;

import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.service.resource.ResourceServerService;
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
    private ResourceServerService resourceServerService;

    @Override
    public SessionCredential load(String serverId) {
        ResourceServerDO e = resourceServerService.getServer(serverId);
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
