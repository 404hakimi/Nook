package com.nook.biz.node.framework.socks5.probe;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * SOCKS5 拨号探测
 *
 * @author nook
 */
@Slf4j
@Component
public class Socks5Prober {

    /** 响应体截断上限. */
    private static final int MAX_RAW_RESPONSE_BYTES = 4096;

    /** 当前线程 SOCKS5 凭据; JDK Authenticator 仅在握手时读. */
    private static final ThreadLocal<PasswordAuthentication> CURRENT_AUTH = new ThreadLocal<>();

    static {
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

    /**
     * 通过 SOCKS5 拨号一个 HTTP(S) echo 端点
     *
     * @param socksHost        SOCKS5 host
     * @param socksPort        SOCKS5 port
     * @param socksUser        用户名 (无鉴权传 null/空)
     * @param socksPass        密码 (无鉴权传 null/空)
     * @param echoUrl          目标 HTTP(S) URL
     * @param connectTimeoutMs TCP 建连超时
     * @param readTimeoutMs    HTTP 读响应超时
     * @return 拨号探测结果
     */
    public Socks5ProbeSnapshot probe(String socksHost, int socksPort, String socksUser, String socksPass,
                                     String echoUrl, int connectTimeoutMs, int readTimeoutMs) {
        long start = System.currentTimeMillis();
        try {
            HttpResult http = doRequest(socksHost, socksPort, socksUser, socksPass,
                    echoUrl, connectTimeoutMs, readTimeoutMs);
            long elapsed = System.currentTimeMillis() - start;
            log.info("[socks5-probe] OK host={}:{} url={} status={} elapsed={}ms",
                    socksHost, socksPort, echoUrl, http.status, elapsed);
            return new Socks5ProbeSnapshot(true, elapsed, echoUrl, connectTimeoutMs, readTimeoutMs,
                    http.status, http.body, null);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[socks5-probe] FAIL host={}:{} url={} elapsed={}ms",
                    socksHost, socksPort, echoUrl, elapsed, e);
            return new Socks5ProbeSnapshot(false, elapsed, echoUrl, connectTimeoutMs, readTimeoutMs,
                    0, null,
                    e.getClass().getSimpleName() + ": " + StrUtil.blankToDefault(e.getMessage(), ""));
        }
    }

    private record HttpResult(int status, String body) { }

    private HttpResult doRequest(String socksHost, int socksPort, String socksUser, String socksPass,
                                 String echoUrl, int connectTimeoutMs, int readTimeoutMs) throws Exception {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(socksHost, socksPort));
        boolean needAuth = StrUtil.isNotBlank(socksUser) && StrUtil.isNotBlank(socksPass);
        if (needAuth) {
            CURRENT_AUTH.set(new PasswordAuthentication(socksUser, socksPass.toCharArray()));
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(echoUrl).toURL().openConnection(proxy);
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "nook-socks5-prober");
            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = ObjectUtil.isNull(stream) ? "" : readCapped(stream);
            return new HttpResult(status, body);
        } finally {
            CURRENT_AUTH.remove();
        }
    }

    private String readCapped(InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buf = new char[1024];
            int read;
            while ((read = r.read(buf)) > 0) {
                int remaining = MAX_RAW_RESPONSE_BYTES - sb.length();
                if (remaining <= 0) {
                    sb.append("\n... (truncated)");
                    break;
                }
                sb.append(buf, 0, Math.min(read, remaining));
            }
        }
        return sb.toString();
    }
}
