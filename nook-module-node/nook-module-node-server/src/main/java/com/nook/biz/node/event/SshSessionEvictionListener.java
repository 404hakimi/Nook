package com.nook.biz.node.event;

import com.nook.framework.ssh.core.SshSessions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 业务侧凭据变更 → SSH 会话失效桥; 把 biz 事件转成 {@link SshSessions#invalidate}.
 *
 * @author nook
 */
@Slf4j
@Component
public class SshSessionEvictionListener {

    /**
     * 监听服务器凭据变更, 失效对应缓存 session, 下次 acquire 用最新凭据重建.
     *
     * @param event 凭据变更事件
     */
    @EventListener
    public void onCredentialChanged(ServerCredentialChangedEvent event) {
        // 凭据变了 → 所有 scope 的 cache session 都要重建; 走 invalidate 不区分 scope
        log.info("[ssh-eviction] 收到 ServerCredentialChangedEvent server={}", event.getServerId());
        SshSessions.invalidate(event.getServerId());
    }
}
