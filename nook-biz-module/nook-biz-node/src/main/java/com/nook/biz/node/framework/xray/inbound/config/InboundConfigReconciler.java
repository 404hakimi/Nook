package com.nook.biz.node.framework.xray.inbound.config;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.framework.xray.XrayTags;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** xray.json inbound 段投影: 用 DB clients 替换每个 inbound 的 settings.clients[]. */
@Slf4j
@Component
public class InboundConfigReconciler {

    /** 用 DB 行替换每个 inbound 的 settings.clients[]; 不动 inbound 本身的 listen/port/streamSettings/protocol. */
    public void repopulateClients(JSONObject root, Map<String, List<XrayClientDO>> byInboundTag) {
        JSONArray inbounds = root.getJSONArray("inbounds");
        if (CollUtil.isEmpty(inbounds)) return;
        for (int i = 0; i < inbounds.size(); i++) {
            JSONObject inbound = inbounds.getJSONObject(i);
            if (ObjectUtil.isNull(inbound)) continue;
            String tag = inbound.getString("tag");
            if (StrUtil.isBlank(tag) || StrUtil.equals(tag, XrayTags.API)) continue;
            String protocolCode = inbound.getString("protocol");
            if (StrUtil.isBlank(protocolCode)) continue;
            // 远端 inbound 找不到对应协议(冷僻协议) → 仍保留它的原 clients 不动, 不破坏运营手工管的 inbound
            InboundProtocolMapping protocol;
            try {
                protocol = InboundProtocolMapping.of(protocolCode);
            } catch (BusinessException be) {
                log.warn("[reconciler] inbound={} 协议 {} 暂不在多协议抽象中, 跳过 client 重建",
                        tag, protocolCode);
                continue;
            }
            JSONObject settings = inbound.getJSONObject("settings");
            if (ObjectUtil.isNull(settings)) {
                settings = new JSONObject();
                inbound.put("settings", settings);
            }
            settings.put("clients", buildClientArray(byInboundTag.get(tag), protocol));
        }
    }

    private JSONArray buildClientArray(List<XrayClientDO> rows, InboundProtocolMapping protocol) {
        JSONArray arr = new JSONArray();
        if (CollUtil.isEmpty(rows)) return arr;
        for (XrayClientDO row : rows) {
            if (!StrUtil.equalsIgnoreCase(row.getProtocol(), protocol.getCode())) continue;
            arr.add(protocol.buildClientJson(toSpec(row)));
        }
        return arr;
    }

    private InboundUserSpec toSpec(XrayClientDO row) {
        return InboundUserSpec.builder()
                .externalInboundRef(row.getExternalInboundRef())
                .email(row.getClientEmail())
                .uuid(row.getClientUuid())
                .protocol(row.getProtocol())
                .build();
    }
}
