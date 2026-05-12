package com.nook.biz.operation.internal.progress.ws;

import com.alibaba.fastjson2.JSON;
import com.nook.biz.operation.api.event.OpProgressEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 进度推送的中央 Hub; 维护 session ↔ 订阅 opId 双向索引, broadcast 时按需推送.
 *
 * <p>双向索引设计:
 * <ul>
 *   <li>sessions: sessionId → session (用于发送)</li>
 *   <li>subscriptions: sessionId → Set&lt;opId&gt; (一个 admin 可能并发多个 op 看进度)</li>
 *   <li>reverseIndex: opId → Set&lt;sessionId&gt; (broadcast 直接定位订阅者, 不用遍历全部 session)</li>
 * </ul>
 *
 * <p>session 断开时同时清三处索引避免泄漏.
 *
 * @author nook
 */
@Slf4j
@Component
public class OpProgressHub {

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> reverseIndex = new ConcurrentHashMap<>();

    void registerSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
        subscriptions.put(session.getId(), Collections.newSetFromMap(new ConcurrentHashMap<>()));
        log.debug("[op-ws] session 注册 sid={} 当前活跃={}", session.getId(), sessions.size());
    }

    void unregisterSession(WebSocketSession session) {
        String sid = session.getId();
        sessions.remove(sid);
        Set<String> ops = subscriptions.remove(sid);
        if (ops != null) {
            // 反向索引按 opId 清掉这个 sid; opId 没人订阅时整个 entry 移除
            for (String opId : ops) {
                reverseIndex.computeIfPresent(opId, (k, set) -> {
                    set.remove(sid);
                    return set.isEmpty() ? null : set;
                });
            }
        }
        log.debug("[op-ws] session 注销 sid={} 当前活跃={}", sid, sessions.size());
    }

    void subscribe(WebSocketSession session, String opId) {
        if (opId == null || opId.isEmpty()) return;
        String sid = session.getId();
        Set<String> ops = subscriptions.get(sid);
        if (ops == null) return;
        ops.add(opId);
        reverseIndex.computeIfAbsent(opId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(sid);
    }

    void unsubscribe(WebSocketSession session, String opId) {
        if (opId == null || opId.isEmpty()) return;
        String sid = session.getId();
        Set<String> ops = subscriptions.get(sid);
        if (ops != null) ops.remove(opId);
        reverseIndex.computeIfPresent(opId, (k, set) -> {
            set.remove(sid);
            return set.isEmpty() ? null : set;
        });
    }

    /**
     * 把事件推给所有订阅了 event.opId 的 session; 没人订阅静默跳过.
     * 单 session 发送失败仅 log, 不阻塞其他订阅者.
     */
    public void broadcast(OpProgressEvent event) {
        if (event == null || event.getOpId() == null) return;
        Set<String> sids = reverseIndex.get(event.getOpId());
        if (sids == null || sids.isEmpty()) return;
        String payload = JSON.toJSONString(event);
        TextMessage msg = new TextMessage(payload);
        for (String sid : sids) {
            WebSocketSession s = sessions.get(sid);
            if (s == null || !s.isOpen()) continue;
            try {
                // synchronized 是必要的: Spring WebSocketSession 不保证多线程并发 sendMessage 安全
                synchronized (s) {
                    s.sendMessage(msg);
                }
            } catch (IOException e) {
                log.warn("[op-ws] send 失败 sid={} opId={}: {}", sid, event.getOpId(), e.getMessage());
            } catch (IllegalStateException ise) {
                // session 已关闭或正在被另一个线程发送; 静默
                log.debug("[op-ws] send 状态异常 sid={} opId={}: {}", sid, event.getOpId(), ise.getMessage());
            }
        }
    }
}
