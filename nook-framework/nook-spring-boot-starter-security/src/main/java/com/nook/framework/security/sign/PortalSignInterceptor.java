package com.nook.framework.security.sign;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端 (/portal/**) 请求验签: HMAC-SHA256(method\nuri\nts\nnonce) + 时间窗 + nonce 防重放.
 * 拦路径在 SaTokenConfig 注册 (订阅链接 /portal/sub/** 豁免, 供第三方客户端直接访问).
 *
 * @author nook
 */
@Slf4j
@Component
public class PortalSignInterceptor implements HandlerInterceptor {

    public static final String HEADER_TS = "X-Nook-Ts";
    public static final String HEADER_NONCE = "X-Nook-Nonce";
    public static final String HEADER_SIGN = "X-Nook-Sign";

    @Value("${nook.portal-sign.enabled:false}")
    private boolean enabled;

    @Value("${nook.portal-sign.secret:}")
    private String secret;

    /** 时间窗 (秒); 请求时间戳偏差超出即拒绝, 也是 nonce 的去重时长. */
    @Value("${nook.portal-sign.window-seconds:300}")
    private long windowSeconds;

    /** nonce → 过期时间戳(ms); 单机内存防重放, 时间窗内去重足够. */
    private final Map<String, Long> seenNonces = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!enabled) {
            return true;
        }
        if (secret == null || secret.isBlank()) {
            log.error("[portal-sign] 已启用但未配置 secret, 跳过校验");
            return true;
        }
        String ts = request.getHeader(HEADER_TS);
        String nonce = request.getHeader(HEADER_NONCE);
        String sign = request.getHeader(HEADER_SIGN);
        if (ts == null || nonce == null || sign == null) {
            return reject(request, response, "缺少签名头");
        }
        long now = System.currentTimeMillis();
        long tsMs;
        try {
            tsMs = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            return reject(request, response, "时间戳非法");
        }
        if (Math.abs(now - tsMs) > windowSeconds * 1000) {
            return reject(request, response, "时间戳超窗");
        }
        String expected = hmacHex(request.getMethod() + "\n" + request.getRequestURI() + "\n" + ts + "\n" + nonce);
        if (!expected.equalsIgnoreCase(sign)) {
            return reject(request, response, "签名不匹配");
        }
        cleanupIfNeeded(now);
        if (seenNonces.putIfAbsent(nonce, now + windowSeconds * 1000) != null) {
            return reject(request, response, "nonce 重放");
        }
        return true;
    }

    /** 容量阈值触发清理过期 nonce, 防内存膨胀. */
    private void cleanupIfNeeded(long now) {
        if (seenNonces.size() > 10_000) {
            seenNonces.entrySet().removeIf(e -> e.getValue() < now);
        }
    }

    private String hmacHex(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean reject(HttpServletRequest request, HttpServletResponse response, String reason)
            throws Exception {
        log.warn("[portal-sign] 拒绝: uri={} reason={}", request.getRequestURI(), reason);
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"签名校验失败\"}");
        return false;
    }
}
