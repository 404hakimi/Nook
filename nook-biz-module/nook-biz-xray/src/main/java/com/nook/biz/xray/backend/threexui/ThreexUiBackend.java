package com.nook.biz.xray.backend.threexui;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.xray.backend.XrayBackend;
import com.nook.biz.xray.backend.XrayBackendType;
import com.nook.biz.resource.api.dto.ServerCredentialDTO;
import com.nook.biz.xray.backend.dto.XrayClientRef;
import com.nook.biz.xray.backend.dto.XrayClientSpec;
import com.nook.biz.xray.backend.dto.XrayClientTraffic;
import com.nook.biz.xray.backend.dto.XrayInboundInfo;
import com.nook.biz.xray.constant.XrayErrorCode;
import com.nook.common.web.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 3x-ui 面板对接实现。
 * 一个实例绑定一台 server；HttpClient/CookieManager 复用，提交给 ThreexUiPanelClient。
 */
public class ThreexUiBackend implements XrayBackend {

    private final ServerCredentialDTO cred;
    private final ThreexUiPanelClient panel;

    public ThreexUiBackend(ServerCredentialDTO cred) {
        this.cred = cred;
        this.panel = new ThreexUiPanelClient(cred);
    }

    @Override
    public XrayBackendType type() {
        return XrayBackendType.THREEXUI;
    }

    @Override
    public String serverId() {
        return cred.serverId();
    }

    @Override
    public void verifyConnectivity() {
        panel.login();
    }

    @Override
    public List<XrayInboundInfo> listInbounds() {
        JSONArray arr = panel.listInbounds();
        List<XrayInboundInfo> out = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            JSONObject ib = arr.getJSONObject(i);
            int clientCount = 0;
            String settingsStr = ib.getString("settings");
            if (StrUtil.isNotBlank(settingsStr)) {
                JSONObject settings = JSON.parseObject(settingsStr);
                JSONArray clients = settings.getJSONArray("clients");
                clientCount = clients == null ? 0 : clients.size();
            }
            out.add(new XrayInboundInfo(
                    String.valueOf(ib.getIntValue("id")),
                    ib.getString("remark"),
                    ib.getString("protocol"),
                    ib.getIntValue("port"),
                    ib.getBooleanValue("enable"),
                    clientCount
            ));
        }
        return out;
    }

    @Override
    public void addClient(XrayClientSpec spec) {
        JSONObject existing = panel.findClient(spec.externalInboundRef(), spec.email());
        if (ObjectUtil.isNotNull(existing)) {
            throw new BusinessException(XrayErrorCode.CLIENT_DUPLICATE, spec.email());
        }
        JSONObject client = buildClientJson(spec);
        panel.addClient(spec.externalInboundRef(), client);
    }

    @Override
    public void delClient(XrayClientRef ref) {
        panel.delClient(ref.externalInboundRef(), ref.uuid());
    }

    @Override
    public XrayClientTraffic getClientTraffic(XrayClientRef ref) {
        JSONObject obj = panel.getClientTraffic(ref.email());
        return new XrayClientTraffic(
                obj.getString("email"),
                obj.getLongValue("up"),
                obj.getLongValue("down"),
                obj.getLongValue("total"),
                obj.getLongValue("expiryTime"),
                obj.getBooleanValue("enable")
        );
    }

    @Override
    public void resetClientTraffic(XrayClientRef ref) {
        panel.resetClientTraffic(ref.externalInboundRef(), ref.email());
    }

    /** 暴露给上层用的"以现有 client 为模板克隆"功能；产线场景不太用，更多是运维/调试。 */
    public String cloneClient(String externalInboundRef, String sourceEmail, String newEmail) {
        return panel.cloneClient(externalInboundRef, sourceEmail, newEmail);
    }

    /**
     * 把 spec 转成 3x-ui 期望的 client JSON。
     * 不同协议字段名略有差异:
     *   vless / vmess  → id (UUID)
     *   trojan         → password (我们就把 spec.uuid 当 password 用)
     *   shadowsocks    → password + method (本期暂不支持)
     */
    private JSONObject buildClientJson(XrayClientSpec spec) {
        JSONObject c = new JSONObject();
        String protocol = StrUtil.blankToDefault(spec.protocol(), "vless").toLowerCase();
        String secret = StrUtil.isNotBlank(spec.uuid()) ? spec.uuid() : UUID.randomUUID().toString();
        switch (protocol) {
            case "vless", "vmess" -> c.put("id", secret);
            case "trojan" -> c.put("password", secret);
            case "shadowsocks", "ss" -> {
                c.put("password", secret);
                c.put("method", "aes-256-gcm"); // 默认值，调用方需要自己改可后续扩 spec
            }
            default -> throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                    cred.serverId(), "未支持的协议: " + protocol);
        }
        c.put("email", spec.email());
        c.put("flow", StrUtil.blankToDefault(spec.flow(), ""));
        c.put("limitIp", spec.limitIp());
        c.put("totalGB", spec.totalBytes());           // 字段名带 GB 但单位是字节，3x-ui 自己定的
        c.put("expiryTime", spec.expiryEpochMillis()); // 毫秒；0=永久
        c.put("enable", true);
        c.put("tgId", "");
        c.put("subId", randomSubId());
        c.put("reset", 0);
        return c;
    }

    private static String randomSubId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
