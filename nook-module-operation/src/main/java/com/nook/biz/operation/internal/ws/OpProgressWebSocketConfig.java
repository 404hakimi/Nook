package com.nook.biz.operation.internal.ws;

import cn.dev33.satoken.exception.NotLoginException;
import cn.hutool.core.util.StrUtil;
import com.nook.framework.security.stp.StpSystemUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 注册 /admin/operation/ws/op-progress 端点 + sa-token 握手鉴权.
 *
 * <p>WebSocket 不走 SaInterceptor (那是 Spring MVC 路径), 必须在握手阶段自己校验 token;
 * 浏览器侧 WebSocket 不能加自定义 header, 用 query 参数 ?token=xxx 传.
 *
 * @author nook
 */
@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class OpProgressWebSocketConfig implements WebSocketConfigurer {

    private final OpProgressHub hub;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new OpProgressWebSocketHandler(hub), "/admin/operation/ws/op-progress")
                .addInterceptors(new SaTokenHandshakeInterceptor())
                // 允许任何来源; 生产用 nginx 同源代理时实际无效, 仅 dev 跨域用
                .setAllowedOrigins("*");
    }

    /** 握手阶段读 ?token=xxx, sa-token 校验未通过返 401. */
    static class SaTokenHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            if (!(request instanceof ServletServerHttpRequest servletReq)) {
                return false;
            }
            String token = servletReq.getServletRequest().getParameter("token");
            if (StrUtil.isBlank(token)) {
                writeUnauthorized(response, "缺少 token");
                return false;
            }
            try {
                // 用 system loginType 校验: getLoginIdByToken 返 null 即 token 失效 / 不匹配 system 体系
                Object loginIdObj = StpSystemUtil.stpLogic().getLoginIdByToken(token);
                if (loginIdObj == null) {
                    writeUnauthorized(response, "token 已失效");
                    return false;
                }
                attributes.put("loginId", loginIdObj.toString());
                attributes.put("token", token);
                return true;
            } catch (NotLoginException nle) {
                writeUnauthorized(response, "未登录: " + nle.getMessage());
                return false;
            } catch (Exception e) {
                log.warn("[op-ws] 握手鉴权失败 token={}: {}",
                        StrUtil.maxLength(token, 8), e.getMessage());
                writeUnauthorized(response, "鉴权异常");
                return false;
            }
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
            // no-op
        }

        private static void writeUnauthorized(ServerHttpResponse response, String reason) {
            if (response instanceof ServletServerHttpResponse servletResp) {
                servletResp.getServletResponse().setStatus(401);
            }
            log.debug("[op-ws] 握手失败: {}", reason);
        }
    }
}
