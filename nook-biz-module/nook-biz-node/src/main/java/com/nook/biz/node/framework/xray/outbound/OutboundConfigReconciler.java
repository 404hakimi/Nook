package com.nook.biz.node.framework.xray.outbound;

import jakarta.annotation.Resource;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.resource.api.ResourceIpPoolApi;
import com.nook.biz.resource.api.dto.IpPoolEntryDTO;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.framework.xray.XrayTags;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * xray.json outbound 段构造: 每个 client 一条 socks5 出站, 加 freedom api / direct 兜底.
 *
 * @author nook
 */
@Slf4j
@Component
public class OutboundConfigReconciler {

    /** 默认直连出站 tag (兜底用). */
    private static final String DIRECT_OUTBOUND_TAG = "direct";

    /** 用户专属 socks5 出站 tag 前缀; 拼接 client email. */
    private static final String USER_OUTBOUND_TAG_PREFIX = "out_";

    @Resource
    private ResourceIpPoolApi resourceIpPoolApi;

    /**
     * 给 client.email 拼对应的 socks5 出站 tag; routing 与 outbound 共用此规则保证 tag 一致.
     *
     * @param email client email
     * @return outbound tag
     */
    public static String userTagFor(String email) {
        return USER_OUTBOUND_TAG_PREFIX + email;
    }

    /**
     * 每个 client 一条 socks5 outbound (拿不到 IP 凭据则跳过), 加 freedom api / direct 兜底.
     *
     * @param rows 当前 server 全部 client
     * @return JSONArray of outbound
     */
    public JSONArray buildOutbounds(List<XrayClientDO> rows) {
        JSONArray outbounds = new JSONArray();
        outbounds.add(freedomOutbound(XrayTags.API));
        outbounds.add(freedomOutbound(DIRECT_OUTBOUND_TAG));
        for (XrayClientDO row : rows) {
            JSONObject socks = buildSocks5OutboundFor(row);
            if (ObjectUtil.isNotNull(socks)) outbounds.add(socks);
        }
        return outbounds;
    }

    /** 单 client → 单 socks5 outbound; IP 池条目缺失或 SOCKS5 凭据不全返回 null 让上层跳过. */
    private JSONObject buildSocks5OutboundFor(XrayClientDO row) {
        IpPoolEntryDTO ip;
        try {
            ip = resourceIpPoolApi.loadEntry(row.getIpId());
        } catch (BusinessException be) {
            log.warn("[reconciler] 跳过 client={} ipId={} (IP 池条目不存在: {})",
                    row.getClientEmail(), row.getIpId(), be.getMessage());
            return null;
        }
        if (StrUtil.isBlank(ip.getSocks5Host()) || ObjectUtil.isNull(ip.getSocks5Port())) {
            log.warn("[reconciler] 跳过 client={} ipId={} (SOCKS5 凭据未配置)",
                    row.getClientEmail(), row.getIpId());
            return null;
        }
        JSONObject server = new JSONObject();
        server.put("address", ip.getSocks5Host());
        server.put("port", ip.getSocks5Port());
        if (StrUtil.isNotBlank(ip.getSocks5Username()) && StrUtil.isNotBlank(ip.getSocks5Password())) {
            JSONObject user = new JSONObject();
            user.put("user", ip.getSocks5Username());
            user.put("pass", ip.getSocks5Password());
            user.put("level", 0);
            JSONArray users = new JSONArray();
            users.add(user);
            server.put("users", users);
        }
        JSONArray servers = new JSONArray();
        servers.add(server);
        JSONObject settings = new JSONObject();
        settings.put("servers", servers);
        JSONObject outbound = new JSONObject();
        outbound.put("tag", userTagFor(row.getClientEmail()));
        outbound.put("protocol", "socks");
        outbound.put("settings", settings);
        return outbound;
    }

    /** freedom 直连出站; api 通道与 direct 兜底共用. */
    private JSONObject freedomOutbound(String tag) {
        JSONObject o = new JSONObject();
        o.put("tag", tag);
        o.put("protocol", "freedom");
        return o;
    }
}
