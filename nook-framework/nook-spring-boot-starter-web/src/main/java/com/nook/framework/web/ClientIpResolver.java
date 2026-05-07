package com.nook.framework.web;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.List;

/** 解析客户端真实 IP，按代理头 → RemoteAddr 顺序回退。 */
public final class ClientIpResolver {

    // 顺序参考 Cloudflare → Nginx → 默认；优先取最可信的 CF-Connecting-IP
    private static final List<String> HEADERS = Arrays.asList(
            "CF-Connecting-IP",
            "X-Real-IP",
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP");

    private ClientIpResolver() {
    }

    /** 从 HttpServletRequest 解析客户端 IP；多级代理的 X-Forwarded-For 取首个非 unknown。 */
    public static String resolve(HttpServletRequest req) {
        if (ObjectUtil.isNull(req)) {
            return null;
        }
        for (String h : HEADERS) {
            String v = req.getHeader(h);
            if (StrUtil.isNotBlank(v) && !"unknown".equalsIgnoreCase(v)) {
                // X-Forwarded-For 可能是 "client, proxy1, proxy2"，取最左侧的 client
                int comma = v.indexOf(',');
                return comma > 0 ? v.substring(0, comma).trim() : v.trim();
            }
        }
        return req.getRemoteAddr();
    }
}
