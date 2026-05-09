package com.nook.biz.node.framework.xray.inbound.config;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.xray.XrayTags;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundSnapshot;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/** 解析远端 xray.json 取 inbound 列表; 跳过 api 通道. */
@Slf4j
public final class InboundConfigParser {

    private InboundConfigParser() {
    }

    public static List<InboundSnapshot> parseInbounds(String json) {
        List<InboundSnapshot> list = new ArrayList<>();
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
            if (StrUtil.isBlank(tag) || StrUtil.equals(tag, XrayTags.API)) continue;
            list.add(new InboundSnapshot(
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
