package com.nook.biz.node.event;

import com.nook.framework.ssh.core.SshSessionManager;
import com.nook.biz.node.event.ServerCredentialChangedEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 业务侧的凭据变更 → SSH 会话失效桥; 把 biz 事件转换成对 framework {@link SshSessionManager} 的 invalidate 调用.
 *
 * <p>放在 service 层而不是 framework 内, 是为了让 framework/ssh 不去 import 业务事件类.
 *
 * @author nook
 */
@Slf4j
@Component
public class SshSessionEvictionListener {

    @Resource
    private SshSessionManager sshSessionManager;

    /**
     * 监听服务器凭据变更, 失效对应缓存 session, 下次 acquire 用最新凭据重建.
     *
     * @param event 凭据变更事件
     */
    @EventListener
    public void onCredentialChanged(ServerCredentialChangedEvent event) {
        // 凭据变了 → 所有 scope 的 cache session 都要重建; 走 invalidateAll 不区分 scope
        log.info("[ssh-eviction] 收到 ServerCredentialChangedEvent server={}", event.getServerId());
        sshSessionManager.invalidateAll(event.getServerId());
    }
}
