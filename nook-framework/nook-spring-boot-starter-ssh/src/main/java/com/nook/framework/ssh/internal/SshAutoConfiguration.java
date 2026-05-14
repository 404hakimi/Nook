package com.nook.framework.ssh.internal;

import com.nook.framework.ssh.config.SshSessionProperties;
import com.nook.framework.ssh.core.SshCredentialApi;
import com.nook.framework.ssh.core.SshSessionManager;
import com.nook.framework.ssh.core.SshSessions;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.core.CoreModuleProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * nook-spring-boot-starter-ssh 自动装配; META-INF/spring/.../AutoConfiguration.imports 引导.
 *
 * @author nook
 */
@AutoConfiguration
@EnableConfigurationProperties(SshSessionProperties.class)
public class SshAutoConfiguration {

    /**
     * MINA SSHD 全局单例; 所有 SshSession 共享同一个 NIO event loop, 销毁时自动 stop.
     *
     * @param props SSH 会话基础参数
     * @return SshClient
     */
    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean
    public SshClient sshClient(SshSessionProperties props) {
        SshClient client = SshClient.setUpDefaultClient();
        CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, props.getHeartbeatInterval());
        CoreModuleProperties.HEARTBEAT_REPLY_WAIT.set(client, props.getHeartbeatReplyWait());
        // 接受任何 host key; 生产化前迁到 KnownHostsServerKeyVerifier.
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        client.start();
        return client;
    }

    /**
     * SSH 会话注册表 bean; 销毁时统一释放所有缓存的 session.
     *
     * @param sshClient MINA 全局 SshClient
     * @param props     SSH 会话基础参数
     * @return SshSessionManager
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public SshSessionManager sshSessionManager(SshClient sshClient, SshSessionProperties props) {
        return new DefaultSshSessionManager(sshClient, props);
    }

    /**
     * 把 SshSessionManager + 业务侧 SshCredentialApi 注入静态 facade {@link SshSessions}.
     * credentialApi 用 @Autowired(required=false) — 没有业务模块时 framework 仍可独立加载 (只用 runAdHoc).
     *
     * @return 占位 bean, 保证 spring 把本方法当组件装配点跑一次
     */
    @Bean
    @SuppressWarnings("InstantiationOfUtilityClass")
    public SshSessions sshSessionsInitializer(SshSessionManager sessionManager,
                                              @Autowired(required = false) SshCredentialApi credentialApi) {
        SshSessions.init(sessionManager, credentialApi);
        return new SshSessions();
    }
}
