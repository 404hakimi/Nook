package com.nook.biz.node.framework.ssh.config;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.core.CoreModuleProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MINA SSHD 装配, 启用 SshSessionProperties 并暴露全局 SshClient bean.
 *
 * @author nook
 */
@Configuration
@EnableConfigurationProperties(SshSessionProperties.class)
public class SshClientConfig {

    /**
     * MINA SSHD 全局单例; 所有 ManagedSession 共享同一个 NIO event loop, 销毁时自动 stop.
     *
     * @param props SSH 会话基础参数
     * @return SshClient
     */
    @Bean(destroyMethod = "stop")
    public SshClient sshClient(SshSessionProperties props) {
        SshClient client = SshClient.setUpDefaultClient();
        CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, props.getHeartbeatInterval());
        CoreModuleProperties.HEARTBEAT_REPLY_WAIT.set(client, props.getHeartbeatReplyWait());
        // 接受任何 host key, 与原 sshj PromiscuousVerifier 行为对齐;
        // 生产化前迁到 KnownHostsServerKeyVerifier (ticket [security] SSH host key 校验).
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        client.start();
        return client;
    }
}
