package com.nook.biz.xray.backend.threexui;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.xray.constant.XrayErrorCode;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 3x-ui 面板 HTTP API 客户端 (production)。
 *
 * 与 demo 版本相比:
 *   - 异常归一化为 BusinessException + XrayErrorCode；
 *   - session 失效自动重 login (检测响应非 JSON 或 success=false 含"login")；
 *   - 单实例对应一台 server；HttpClient + CookieManager 内部维护，调用方不操心；
 *   - 线程安全：所有方法可并发调用，HttpClient/CookieManager 自身线程安全，登录用 AtomicBoolean 防并发。
 *
 * 端点参考 MHSanaei/3x-ui v2.x web/controller/api。
 */
@Slf4j
public class ThreexUiPanelClient {

    private final ServerCredentialDTO cred;
    private final String baseUrl;
    private final HttpClient http;
    /** 简单的"登录中"标记，避免并发首次调用时多次 login(每个调用方仍会走 login，但浪费一两次而已，能接受)。 */
    private final AtomicBoolean loggedIn = new AtomicBoolean(false);

    public ThreexUiPanelClient(ServerCredentialDTO cred) {
        if (StrUtil.isBlank(cred.panelBaseUrl()) || StrUtil.isBlank(cred.panelUsername())
                || StrUtil.isBlank(cred.panelPassword())) {
            throw new BusinessException(XrayErrorCode.SERVER_CREDENTIAL_INVALID, cred.serverId());
        }
        this.cred = cred;
        this.baseUrl = trimTrailingSlash(cred.panelBaseUrl());
        HttpClient.Builder builder = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofSeconds(cred.backendTimeoutSecondsOrDefault()))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (cred.panelIgnoreTls()) {
            builder.sslContext(trustAllSslContext());
        }
        this.http = builder.build();
    }

    /** 登录 + 缓存 cookie；并发调用幂等。 */
    public void login() {
        long start = System.currentTimeMillis();
        log.info("[3xui] login start server={} url={} user={}",
                cred.serverId(), baseUrl, cred.panelUsername());
        String form = "username=" + URLEncoder.encode(cred.panelUsername(), StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(cred.panelPassword(), StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(uri("/login"))
                .timeout(Duration.ofSeconds(cred.backendTimeoutSecondsOrDefault()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> resp = send(req);
        JSONObject body = parseJson(resp);
        if (!body.getBooleanValue("success")) {
            log.warn("[3xui] login REJECT server={} url={} elapsed={}ms msg={}",
                    cred.serverId(), baseUrl, System.currentTimeMillis() - start, body.getString("msg"));
            throw new BusinessException(XrayErrorCode.BACKEND_AUTH_FAILED, cred.serverId());
        }
        loggedIn.set(true);
        log.info("[3xui] login OK server={} elapsed={}ms",
                cred.serverId(), System.currentTimeMillis() - start);
    }

    public JSONArray listInbounds() {
        JSONObject resp = call(() -> get("/panel/api/inbounds/list"));
        JSONArray arr = resp.getJSONArray("obj");
        return arr != null ? arr : new JSONArray();
    }

    /** 从 listInbounds 的某行里把 settings 字符串解析回 clients 数组；inbound 不存在或没有 clients 都返回空数组。 */
    public JSONArray listClientsInInbound(String externalInboundRef) {
        JSONArray inbounds = listInbounds();
        for (int i = 0; i < inbounds.size(); i++) {
            JSONObject ib = inbounds.getJSONObject(i);
            if (StrUtil.equals(externalInboundRef, String.valueOf(ib.getIntValue("id")))) {
                String settingsStr = ib.getString("settings");
                if (StrUtil.isBlank(settingsStr)) return new JSONArray();
                JSONObject settings = JSON.parseObject(settingsStr);
                JSONArray clients = settings.getJSONArray("clients");
                return clients != null ? clients : new JSONArray();
            }
        }
        return new JSONArray();
    }

    public JSONObject findClient(String externalInboundRef, String email) {
        JSONArray clients = listClientsInInbound(externalInboundRef);
        for (int i = 0; i < clients.size(); i++) {
            JSONObject c = clients.getJSONObject(i);
            if (StrUtil.equals(email, c.getString("email"))) return c;
        }
        return null;
    }

    public void addClient(String externalInboundRef, JSONObject client) {
        JSONObject settings = new JSONObject();
        JSONArray arr = new JSONArray();
        arr.add(client);
        settings.put("clients", arr);

        JSONObject reqBody = new JSONObject();
        reqBody.put("id", parseInboundIdInt(externalInboundRef));
        // 注意：3x-ui 的 settings 字段是 "JSON 字符串"，不是嵌套对象
        reqBody.put("settings", settings.toJSONString());

        JSONObject resp = call(() -> postJson("/panel/api/inbounds/addClient", reqBody.toJSONString()));
        if (!resp.getBooleanValue("success")) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    cred.serverId(), "addClient: " + resp.getString("msg"));
        }
    }

    public void delClient(String externalInboundRef, String clientUuid) {
        int inboundId = parseInboundIdInt(externalInboundRef);
        JSONObject resp = call(() -> postJson("/panel/api/inbounds/" + inboundId + "/delClient/" + clientUuid, "{}"));
        if (!resp.getBooleanValue("success")) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    cred.serverId(), "delClient: " + resp.getString("msg"));
        }
    }

    public JSONObject getClientTraffic(String email) {
        JSONObject resp = call(() -> get("/panel/api/inbounds/getClientTraffics/"
                + URLEncoder.encode(email, StandardCharsets.UTF_8)));
        JSONObject obj = resp.getJSONObject("obj");
        if (ObjectUtil.isNull(obj)) {
            throw new BusinessException(XrayErrorCode.CLIENT_NOT_FOUND, email);
        }
        return obj;
    }

    public void resetClientTraffic(String externalInboundRef, String email) {
        int inboundId = parseInboundIdInt(externalInboundRef);
        JSONObject resp = call(() -> postJson(
                "/panel/api/inbounds/" + inboundId + "/resetClientTraffic/"
                        + URLEncoder.encode(email, StandardCharsets.UTF_8), "{}"));
        if (!resp.getBooleanValue("success")) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    cred.serverId(), "resetClientTraffic: " + resp.getString("msg"));
        }
    }

    /** 给 cloneClient 这种"业务编排"留接口；与 demo 版本一致，从模板复制 -> 改 id/email/subId -> addClient。 */
    public String cloneClient(String externalInboundRef, String sourceEmail, String newEmail) {
        if (StrUtil.equals(sourceEmail, newEmail)) {
            throw new IllegalArgumentException("newEmail 不能与 sourceEmail 相同");
        }
        JSONObject src = findClient(externalInboundRef, sourceEmail);
        if (ObjectUtil.isNull(src)) {
            throw new BusinessException(XrayErrorCode.CLIENT_NOT_FOUND, sourceEmail);
        }
        JSONObject newClient = JSON.parseObject(src.toJSONString());
        String newUuid = UUID.randomUUID().toString();
        newClient.put("id", newUuid);
        newClient.put("email", newEmail);
        newClient.put("subId", randomSubId());
        newClient.put("up", 0);
        newClient.put("down", 0);
        newClient.put("reset", 0);
        newClient.put("enable", true);
        addClient(externalInboundRef, newClient);
        return newUuid;
    }

    // ===== HTTP / 鉴权基础 =====

    /**
     * 第一次没 login 先 login；响应像是会话失效就重 login 一次再重试。
     * <p>三类失效信号都判：HTTP 401/403、HTML 响应体(被重定向到登录页)、JSON success=false 且 msg 含 login 关键字。
     */
    private JSONObject call(java.util.function.Supplier<HttpResponse<String>> action) {
        if (!loggedIn.get()) login();
        HttpResponse<String> resp = action.get();
        if (looksLikeAuthLost(resp)) {
            log.info("[3xui] session 失效, server={}, 重新 login 后重试", cred.serverId());
            loggedIn.set(false);
            login();
            resp = action.get();
        }
        return parseJson(resp);
    }

    private static boolean looksLikeAuthLost(HttpResponse<String> resp) {
        if (resp.statusCode() == 401 || resp.statusCode() == 403) return true;
        String body = resp.body();
        if (StrUtil.isBlank(body)) return false;
        // 检 HTML——3x-ui 会话失效偶尔走重定向，followRedirect=NORMAL 之后落到登录页 HTML
        String head = body.length() > 32 ? body.substring(0, 32).trim().toLowerCase() : body.trim().toLowerCase();
        if (head.startsWith("<!doctype") || head.startsWith("<html")) return true;
        // 检 JSON {success:false, msg:"please login first"}——更隐蔽，纯靠 status code/HTML 抓不到
        try {
            JSONObject obj = JSON.parseObject(body);
            if (obj != null && !obj.getBooleanValue("success") && StrUtil.isNotBlank(obj.getString("msg"))) {
                String lower = obj.getString("msg").toLowerCase();
                return lower.contains("login") || lower.contains("登录") || lower.contains("unauthorized");
            }
        } catch (Exception ignored) {
            // 不是 JSON 也不是 HTML 直接放行，让上层 parseJson 抛 BACKEND_RESPONSE_INVALID
        }
        return false;
    }

    private HttpResponse<String> get(String path) {
        return send(HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(cred.backendTimeoutSecondsOrDefault()))
                .GET()
                .build());
    }

    private HttpResponse<String> postJson(String path, String json) {
        return send(HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(cred.backendTimeoutSecondsOrDefault()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build());
    }

    private HttpResponse<String> send(HttpRequest req) {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // 把真实 IO 错误打出来——SSL/连接超时/DNS 解析失败/对端 reset 都走这条
            log.warn("[3xui] HTTP IO 失败 server={} uri={} err={}: {}",
                    cred.serverId(), req.uri(), e.getClass().getSimpleName(), e.getMessage());
            throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, cred.serverId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[3xui] HTTP 被中断 server={} uri={}", cred.serverId(), req.uri());
            throw new BusinessException(XrayErrorCode.BACKEND_UNREACHABLE, cred.serverId());
        }
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    private JSONObject parseJson(HttpResponse<String> resp) {
        if (resp.statusCode() / 100 != 2) {
            throw new BusinessException(XrayErrorCode.BACKEND_RESPONSE_INVALID,
                    cred.serverId(), "HTTP " + resp.statusCode() + " " + truncate(resp.body()));
        }
        try {
            return JSON.parseObject(resp.body());
        } catch (JSONException e) {
            throw new BusinessException(XrayErrorCode.BACKEND_RESPONSE_INVALID,
                    cred.serverId(), "非 JSON 响应: " + truncate(resp.body()));
        }
    }

    private static int parseInboundIdInt(String externalInboundRef) {
        try {
            return Integer.parseInt(externalInboundRef.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(XrayErrorCode.INBOUND_NOT_FOUND, externalInboundRef);
        }
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    private static String randomSubId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static SSLContext trustAllSslContext() {
        try {
            TrustManager[] tms = {new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tms, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
