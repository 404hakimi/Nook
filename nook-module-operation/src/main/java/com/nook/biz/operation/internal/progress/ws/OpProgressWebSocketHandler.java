package com.nook.biz.operation.internal.progress.ws;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 接 admin 端浏览器的 WebSocket; 处理 subscribe / unsubscribe 协议消息.
 *
 * <p>协议:
 * <pre>
 *   客户端 → 服务端:
 *     {"action":"subscribe","opId":"..."}      订阅一个 op 的进度
 *     {"action":"unsubscribe","opId":"..."}    取消订阅
 *     {"action":"ping"}                        心跳保活, 服务端不回复
 *
 *   服务端 → 客户端: OpProgressEvent JSON (字段含 opId / status / progressPct / currentStep / ...)
 * </pre>
 *
 * @author nook
 */
@Slf4j
@RequiredArgsConstructor
public class OpProgressWebSocketHandler extends TextWebSocketHandler {

    private final OpProgressHub hub;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        hub.registerSession(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        if (payload == null || payload.isEmpty()) return;
        JSONObject json;
        try {
            json = JSON.parseObject(payload);
        } catch (Exception ex) {
            log.warn("[op-ws] 收到非 JSON 消息 sid={} payload={}", session.getId(), payload);
            return;
        }
        String action = json.getString("action");
        if (action == null) return;
        switch (action) {
            case "subscribe" -> hub.subscribe(session, json.getString("opId"));
            case "unsubscribe" -> hub.unsubscribe(session, json.getString("opId"));
            case "ping" -> { /* 心跳静默吞, 客户端只是用来撑活 idle 连接 */ }
            default -> log.warn("[op-ws] 未知 action={} sid={}", action, session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.unregisterSession(session);
    }
}
