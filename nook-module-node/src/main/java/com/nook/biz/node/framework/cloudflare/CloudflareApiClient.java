package com.nook.biz.node.framework.cloudflare;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cloudflare DNS API 客户端; 仅 A 记录 / zone lookup, acme.sh 自己用 TXT _acme-challenge.
 * <p>API Token 推荐权限: Zone:Read + Zone.DNS:Edit (限定到目标 zone).
 *
 * @author nook
 */
@Slf4j
@Component
public class CloudflareApiClient {

    private static final String API_BASE = "https://api.cloudflare.com/client/v4";
    private static final int TIMEOUT_MS = 10_000;

    /**
     * 自动找 zone + upsert A 记录; 业务侧调这一个就够.
     * 失败抛 BusinessException, 由调用方决定 warn 或终止.
     *
     * @param apiToken    Cloudflare API Token
     * @param fullDomain  完整域名 (e.g. server01.example.com); 最后两段当 zone candidate
     * @param ip          目标 IP (A 记录 content)
     * @param proxied     true=走 CF 代理 (橙云), false=DNS only (灰云)
     */
    public void ensureARecord(String apiToken, String fullDomain, String ip, boolean proxied) {
        String rootDomain = extractRootDomain(fullDomain);
        String zoneId = findZoneId(apiToken, rootDomain);
        upsertARecord(apiToken, zoneId, fullDomain, ip, proxied);
    }

    /** GET /zones?name=<root>; 返第一个匹配 zone 的 id. */
    public String findZoneId(String apiToken, String rootDomain) {
        String url = API_BASE + "/zones?name=" + rootDomain;
        JSONObject body = httpGet(apiToken, url);
        JSONArray result = body.getJSONArray("result");
        if (result == null || result.isEmpty()) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, rootDomain,
                    "Cloudflare 未找到 zone (root=" + rootDomain + "); 检查 token 是否覆盖该 zone");
        }
        String zoneId = result.getJSONObject(0).getString("id");
        log.info("[cf-api] findZoneId root={} zoneId={}", rootDomain, zoneId);
        return zoneId;
    }

    /**
     * 先 GET /zones/{zoneId}/dns_records?name=<full>&type=A 查现存记录; 有则 PUT 覆盖, 无则 POST 新建.
     * 幂等, 重复部署不报 "已存在".
     */
    public void upsertARecord(String apiToken, String zoneId, String fullDomain, String ip, boolean proxied) {
        String listUrl = API_BASE + "/zones/" + zoneId + "/dns_records?name=" + fullDomain + "&type=A";
        JSONArray existing = httpGet(apiToken, listUrl).getJSONArray("result");

        JSONObject payload = new JSONObject();
        payload.put("type", "A");
        payload.put("name", fullDomain);
        payload.put("content", ip);
        payload.put("ttl", 60);
        payload.put("proxied", proxied);

        if (existing != null && !existing.isEmpty()) {
            String recordId = existing.getJSONObject(0).getString("id");
            String putUrl = API_BASE + "/zones/" + zoneId + "/dns_records/" + recordId;
            httpJson(apiToken, putUrl, "PUT", payload);
            log.info("[cf-api] updateA name={} ip={} proxied={}", fullDomain, ip, proxied);
        } else {
            String postUrl = API_BASE + "/zones/" + zoneId + "/dns_records";
            httpJson(apiToken, postUrl, "POST", payload);
            log.info("[cf-api] createA name={} ip={} proxied={}", fullDomain, ip, proxied);
        }
    }

    /** fullDomain 最后两段当 zone candidate (e.g. a.b.example.com → example.com); 不处理 .co.uk 这种次级 TLD. */
    static String extractRootDomain(String fullDomain) {
        String[] parts = fullDomain.split("\\.");
        if (parts.length < 2) {
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, fullDomain,
                    "域名格式非法 (至少 sub.tld): " + fullDomain);
        }
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    private JSONObject httpGet(String apiToken, String url) {
        HttpResponse resp = HttpRequest.get(url)
                .bearerAuth(apiToken)
                .timeout(TIMEOUT_MS)
                .execute();
        return parseAndCheck(resp, "GET " + url);
    }

    private void httpJson(String apiToken, String url, String method, JSONObject payload) {
        HttpRequest req = "PUT".equalsIgnoreCase(method)
                ? HttpRequest.put(url) : HttpRequest.post(url);
        HttpResponse resp = req
                .bearerAuth(apiToken)
                .header("Content-Type", "application/json")
                .body(payload.toJSONString())
                .timeout(TIMEOUT_MS)
                .execute();
        parseAndCheck(resp, method + " " + url);
    }

    /** CF API 统一返回 {success, errors[], result}; success=false 抛业务错; 网络错抛 IO. */
    private JSONObject parseAndCheck(HttpResponse resp, String tag) {
        String body = resp.body();
        if (!resp.isOk()) {
            log.warn("[cf-api] {} HTTP {} body={}", tag, resp.getStatus(), StrUtil.maxLength(body, 300));
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, "cloudflare",
                    "CF API HTTP " + resp.getStatus());
        }
        JSONObject json = JSONObject.parseObject(body);
        if (!Boolean.TRUE.equals(json.getBoolean("success"))) {
            String errMsg = json.getJSONArray("errors") != null
                    ? json.getJSONArray("errors").toJSONString()
                    : StrUtil.maxLength(body, 300);
            log.warn("[cf-api] {} success=false errors={}", tag, errMsg);
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, "cloudflare",
                    "CF API 错误: " + StrUtil.maxLength(errMsg, 200));
        }
        return json;
    }
}
