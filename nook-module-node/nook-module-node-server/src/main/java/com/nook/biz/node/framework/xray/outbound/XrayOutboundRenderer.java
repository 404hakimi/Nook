package com.nook.biz.node.framework.xray.outbound;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

/**
 * Xray socks5 出站 (ado) 入参渲染器; 出站打到落地机, 协议无关
 *
 * @author nook
 */
@Component
public class XrayOutboundRenderer {

    /**
     * 渲染 socks5 出站 JSON (ado 入参; 含可选鉴权)
     *
     * <p>v25.10.15 起 outbound 要求扁平: settings 直接平铺 address / port / user / pass.
     *
     * @param tag      出站 tag
     * @param host     落地机出网 IP
     * @param port     socks5 端口
     * @param username socks5 用户名 (可空 = 不鉴权)
     * @param password socks5 密码 (可空 = 不鉴权)
     * @return ado 入参 JSON
     */
    public String socksOutboundJson(String tag, String host, int port, String username, String password) {
        JSONObject settings = new JSONObject();
        settings.put("address", host);
        settings.put("port", port);
        if (StrUtil.isNotBlank(username) && StrUtil.isNotBlank(password)) {
            settings.put("user", username);
            settings.put("pass", password);
            settings.put("level", 0);
        }
        JSONObject outbound = new JSONObject();
        outbound.put("tag", tag);
        outbound.put("protocol", "socks");
        outbound.put("settings", settings);
        JSONArray outbounds = new JSONArray();
        outbounds.add(outbound);
        JSONObject config = new JSONObject();
        config.put("outbounds", outbounds);
        return config.toJSONString();
    }
}
