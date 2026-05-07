package com.nook.biz.xray.demo;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

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

/**
 * 3x-ui 面板 HTTP API 封装。
 * 端点参考官方仓库 MHSanaei/3x-ui v2.x 的 web/controller/api。
 *
 * 流程：
 *   1) login() 拿到 session cookie；后续所有请求需要带 cookie
 *   2) 业务方法返回 fastjson2 的 JSONObject/JSONArray，调用方按需取字段
 *
 * 鉴权细节：3x-ui 没有显式 token，靠 cookie。HttpClient 内部 CookieManager 自动处理。
 */
public class ThreexUiPanelClient {

    private final DemoConfig cfg;
    private final HttpClient http;

    public ThreexUiPanelClient(DemoConfig cfg) {
        this.cfg = cfg;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofSeconds(cfg.timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (cfg.panelIgnoreTls) {
            builder.sslContext(trustAllSslContext());
        }
        this.http = builder.build();
    }

    /** 登录，cookie 由 HttpClient 自动管理。失败抛异常。 */
    public void login() throws IOException, InterruptedException {
        String form = "username=" + URLEncoder.encode(cfg.panelUsername, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(cfg.panelPassword, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.panelBaseUrl + "/login"))
                .timeout(Duration.ofSeconds(cfg.timeoutSeconds))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JSONObject body = parseObj(resp);
        if (!body.getBooleanValue("success")) {
            throw new IOException("登录失败: " + body.getString("msg"));
        }
    }

    /** 列出所有 inbound（含 settings 字段，里面是 JSON 字符串形式的 clients 数组）。 */
    public JSONArray listInbounds() throws IOException, InterruptedException {
        return parseObj(get("/panel/api/inbounds/list")).getJSONArray("obj");
    }

    /**
     * 给指定 inbound 加一个客户端。
     * @param inboundId  inbound 的 id（list 接口 obj[i].id）
     * @param spec       客户端基本信息
     * @return 服务端返回的整个 response 对象，业务码在 success/msg
     */
    public JSONObject addClient(int inboundId, ClientSpec spec) throws IOException, InterruptedException {
        JSONObject client = new JSONObject();
        client.put("id", spec.uuid);
        client.put("flow", spec.flow == null ? "" : spec.flow);
        client.put("email", spec.email);
        client.put("limitIp", spec.limitIp);
        // 流量上限：3x-ui 用 totalGB(byte) 字段——0 表示不限
        client.put("totalGB", spec.totalBytes);
        // 到期时间：毫秒；0 表示不限期
        client.put("expiryTime", spec.expiryEpochMillis);
        client.put("enable", true);
        client.put("tgId", "");
        client.put("subId", spec.subId == null ? randomSubId() : spec.subId);
        client.put("reset", 0);

        JSONObject settings = new JSONObject();
        JSONArray arr = new JSONArray();
        arr.add(client);
        settings.put("clients", arr);

        JSONObject reqBody = new JSONObject();
        reqBody.put("id", inboundId);
        // 注意：settings 字段是 "JSON 字符串"，不是嵌套对象
        reqBody.put("settings", settings.toJSONString());

        return parseObj(postJson("/panel/api/inbounds/addClient", reqBody.toJSONString()));
    }

    /** 删除某 inbound 下的某客户端（通过客户端 UUID）。 */
    public JSONObject delClient(int inboundId, String clientUuid) throws IOException, InterruptedException {
        return parseObj(postJson("/panel/api/inbounds/" + inboundId + "/delClient/" + clientUuid, "{}"));
    }

    /**
     * 以现有 client 为模板复制一份。流量上限/到期时间/限 IP/flow/tgId 全部沿用，
     * 只换 id(UUID)/email/subId，省去手动构造 ClientSpec。
     *
     * @param inboundId    所在 inbound id
     * @param sourceEmail  模板客户端的 email
     * @param newEmail     新客户端的 email；不能与模板相同
     * @return 服务端返回；附带 newUuid/newEmail 字段方便上层后续调用
     */
    public JSONObject cloneClient(int inboundId, String sourceEmail, String newEmail)
            throws IOException, InterruptedException {
        if (sourceEmail.equals(newEmail)) {
            throw new IllegalArgumentException("newEmail 不能与 sourceEmail 相同");
        }
        JSONObject src = findClient(inboundId, sourceEmail);
        if (src == null) {
            throw new IOException("源客户端不存在: inbound=" + inboundId + " email=" + sourceEmail);
        }
        // 深拷贝以免污染原对象
        JSONObject newClient = JSON.parseObject(src.toJSONString());
        String newUuid = UUID.randomUUID().toString();
        newClient.put("id", newUuid);
        newClient.put("email", newEmail);
        newClient.put("subId", randomSubId());
        newClient.put("reset", 0);
        // 累计流量字段如果存在，复制时清零；服务端通常不读，写上保险
        newClient.put("up", 0);
        newClient.put("down", 0);
        newClient.put("enable", true);

        JSONObject settings = new JSONObject();
        JSONArray arr = new JSONArray();
        arr.add(newClient);
        settings.put("clients", arr);

        JSONObject reqBody = new JSONObject();
        reqBody.put("id", inboundId);
        reqBody.put("settings", settings.toJSONString());

        JSONObject resp = parseObj(postJson("/panel/api/inbounds/addClient", reqBody.toJSONString()));
        // 把上层关心的字段塞进 obj，方便 demo 直接打印
        resp.put("newUuid", newUuid);
        resp.put("newEmail", newEmail);
        return resp;
    }

    /** 根据 email 在指定 inbound 的 settings.clients 里查 client，没找到返回 null。 */
    public JSONObject findClient(int inboundId, String email) throws IOException, InterruptedException {
        JSONArray inbounds = listInbounds();
        for (int i = 0; i < inbounds.size(); i++) {
            JSONObject ib = inbounds.getJSONObject(i);
            if (ib.getIntValue("id") != inboundId) continue;
            String settingsStr = ib.getString("settings");
            if (settingsStr == null || settingsStr.isEmpty()) return null;
            JSONObject settings = JSON.parseObject(settingsStr);
            JSONArray clients = settings.getJSONArray("clients");
            if (clients == null) return null;
            for (int j = 0; j < clients.size(); j++) {
                JSONObject c = clients.getJSONObject(j);
                if (email.equals(c.getString("email"))) return c;
            }
            return null;
        }
        return null;
    }

    /** 按 email 查客户端的实时流量（up/down/total/expiryTime）。 */
    public JSONObject getClientTraffic(String email) throws IOException, InterruptedException {
        return parseObj(get("/panel/api/inbounds/getClientTraffics/" + URLEncoder.encode(email, StandardCharsets.UTF_8)))
                .getJSONObject("obj");
    }

    /** 把客户端的累计流量计数清零（不删客户端）。 */
    public JSONObject resetClientTraffic(int inboundId, String email) throws IOException, InterruptedException {
        return parseObj(postJson("/panel/api/inbounds/" + inboundId + "/resetClientTraffic/"
                + URLEncoder.encode(email, StandardCharsets.UTF_8), "{}"));
    }

    /** 当前在线的 client email 列表。 */
    public JSONArray onlines() throws IOException, InterruptedException {
        return parseObj(postJson("/panel/api/inbounds/onlines", "{}")).getJSONArray("obj");
    }

    /** 服务器状态（CPU/MEM/磁盘/网络），来自面板自带的 /server/status。 */
    public JSONObject serverStatus() throws IOException, InterruptedException {
        return parseObj(postJson("/server/status", "{}")).getJSONObject("obj");
    }

    // ===== HTTP 基础 =====

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.panelBaseUrl + path))
                .timeout(Duration.ofSeconds(cfg.timeoutSeconds))
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> postJson(String path, String json) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.panelBaseUrl + path))
                .timeout(Duration.ofSeconds(cfg.timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static JSONObject parseObj(HttpResponse<String> resp) throws IOException {
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        try {
            return JSON.parseObject(resp.body());
        } catch (Exception e) {
            throw new IOException("响应不是合法 JSON（可能 cookie 失效跳了登录页）: " + resp.body(), e);
        }
    }

    private static String randomSubId() {
        // 3x-ui 默认 16 位字母数字
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

    /** 客户端创建参数。totalBytes=0 不限流量；expiryEpochMillis=0 不限期。 */
    public record ClientSpec(
            String email,
            String uuid,
            int limitIp,
            long totalBytes,
            long expiryEpochMillis,
            String flow,
            String subId
    ) {
        public static ClientSpec basic(String email) {
            return new ClientSpec(email, UUID.randomUUID().toString(), 0, 0, 0, "", null);
        }
    }
}
