package com.nook.biz.node.framework.socks5.probe;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 通过 SOCKS5 拨号公网 echo-IP 服务, 拿到出网真实 IP; 零第三方依赖, 走 JDK 原生 HttpURLConnection.
 *
 * <p>线程安全: SOCKS5 鉴权用全局 {@link Authenticator}; 多并发请求时各线程的 username/password 通过
 * 一个 ThreadLocal 暂存, 避免互相覆盖.
 */
@Slf4j
@Component
public class Socks5Prober {

    /** 公网 echo-IP 端点, 纯文本返回出网 IP; ipify 国内/海外都稳定且无 rate-limit. */
    private static final String ECHO_URL = "https://api.ipify.org/";

    /** TCP 建连 + 读响应总耗时上限; 单次探测不应久于此, 避免前端等太久. */
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    /** 给 JDK Authenticator 用的当前线程 SOCKS5 凭据, RAII 模式 set/clear. */
    private static final ThreadLocal<PasswordAuthentication> CURRENT_AUTH = new ThreadLocal<>();

    static {
        // JDK 默认 Authenticator 接受任何 RequestType, 我们仅在 SOCKS5 鉴权请求时返回当前线程的凭据;
        // 其它场景 (HTTP 401 等) 返回 null 以免污染.
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() != RequestorType.SERVER && getRequestorType() != RequestorType.PROXY) {
                    return null;
                }
                return CURRENT_AUTH.get();
            }
        });
    }

    /** 拨号 + 读 echo-ip; 任何异常都包装成 success=false 结构化结果, 上层不需 try/catch. */
    public Socks5ProbeSnapshot probe(String socksHost, int socksPort, String socksUser, String socksPass) {
        long start = System.currentTimeMillis();
        try {
            String exitIp = probeExitIp(socksHost, socksPort, socksUser, socksPass);
            long elapsed = System.currentTimeMillis() - start;
            log.info("[socks5-probe] OK host={}:{} exitIp={} elapsed={}ms",
                    socksHost, socksPort, exitIp, elapsed);
            return new Socks5ProbeSnapshot(true, elapsed, exitIp, null);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[socks5-probe] FAIL host={}:{} elapsed={}ms",
                    socksHost, socksPort, elapsed, e);
            return new Socks5ProbeSnapshot(false, elapsed, null,
                    e.getClass().getSimpleName() + ": " + StrUtil.blankToDefault(e.getMessage(), ""));
        }
    }

    private String probeExitIp(String socksHost, int socksPort, String socksUser, String socksPass) throws Exception {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(socksHost, socksPort));
        boolean needAuth = StrUtil.isNotBlank(socksUser) && StrUtil.isNotBlank(socksPass);
        if (needAuth) {
            CURRENT_AUTH.set(new PasswordAuthentication(socksUser, socksPass.toCharArray()));
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(ECHO_URL).toURL().openConnection(proxy);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "nook-socks5-prober");
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("echo-ip 端点 HTTP " + code);
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line = r.readLine();
                if (StrUtil.isBlank(line)) {
                    throw new IllegalStateException("echo-ip 端点返回空");
                }
                return line.trim();
            }
        } finally {
            CURRENT_AUTH.remove();
        }
    }
}
