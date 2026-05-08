package com.nook.biz.xray.backend.grpc;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.xray.backend.dto.XrayInboundInfo;
import com.nook.biz.xray.constant.XrayConstants;
import com.nook.biz.xray.constant.XrayErrorCode;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 远端 xray.json 解析: SSH 把整份 config 拉到本地后取 inbound 列表给运营关联用。
 * api 通道(dokodemo-door)对外屏蔽; 协议无关 — 不区分 vmess/vless/trojan, 由 inbound.protocol 字段决定。
 */
@Slf4j
final class XrayConfigParser {

    private XrayConfigParser() {
    }

    static List<XrayInboundInfo> parseInbounds(String json) {
        List<XrayInboundInfo> list = new ArrayList<>();
        if (StrUtil.isBlank(json)) return list;
        JSONObject root;
        try {
            root = JSONObject.parseObject(json);
        } catch (JSONException e) {
            log.warn("远端 xray.json 解析失败", e);
            throw new BusinessException(XrayErrorCode.BACKEND_RESPONSE_INVALID, e,
                    "<remote>", "xray.json 解析失败: " + e.getMessage());
        }
        if (ObjectUtil.isNull(root)) return list;
        JSONArray inbounds = root.getJSONArray("inbounds");
        if (CollUtil.isEmpty(inbounds)) return list;
        for (int i = 0; i < inbounds.size(); i++) {
            JSONObject ib = inbounds.getJSONObject(i);
            if (ObjectUtil.isNull(ib)) continue;
            String tag = ib.getString("tag");
            // 跳过 nook 自管的 api 通道, 不让运营误选到
            if (StrUtil.isBlank(tag) || StrUtil.equals(tag, XrayConstants.API_TAG)) continue;
            list.add(new XrayInboundInfo(
                    tag,
                    StrUtil.blankToDefault(ib.getString("remark"), tag),
                    ib.getString("protocol"),
                    ib.getIntValue("port", 0),
                    true, // nook 标配 config 不写 enabled, 默认 true
                    countClients(ib)));
        }
        return list;
    }

    /** 数 settings.clients 的长度; 非 client-based inbound (dokodemo / freedom) 返回 0。 */
    private static int countClients(JSONObject inbound) {
        JSONObject settings = inbound.getJSONObject("settings");
        if (ObjectUtil.isNull(settings)) return 0;
        JSONArray clients = settings.getJSONArray("clients");
        return CollUtil.isEmpty(clients) ? 0 : clients.size();
    }
}
