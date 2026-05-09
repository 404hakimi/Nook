package com.nook.biz.node.framework.xray.routing;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.dal.dataobject.client.XrayClientDO;
import com.nook.biz.node.framework.xray.XrayTags;
import com.nook.biz.node.framework.xray.outbound.OutboundConfigReconciler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * xray.json routing 段构造: api 通道直走 api 出站, 业务流量按 client.email 分流到对应 socks5 出站.
 *
 * @author nook
 */
@Component
public class RoutingConfigReconciler {

    /**
     * 构造 routing JSON: api 通道直走 api outbound, 其余按 email 分流到该用户的 socks5 outbound.
     *
     * @param rows 当前 server 全部 client
     * @return JSONObject (含 domainStrategy + rules)
     */
    public JSONObject buildRouting(List<XrayClientDO> rows) {
        JSONObject routing = new JSONObject();
        routing.put("domainStrategy", "AsIs");
        JSONArray rules = new JSONArray();
        rules.add(apiRoutingRule());
        for (XrayClientDO row : rows) {
            if (StrUtil.isBlank(row.getClientEmail())) continue;
            rules.add(emailRoutingRule(row));
        }
        routing.put("rules", rules);
        return routing;
    }

    /** api 通道单独 rule: api inbound → api outbound, 永远绕过出站代理. */
    private JSONObject apiRoutingRule() {
        JSONObject rule = new JSONObject();
        rule.put("type", "field");
        JSONArray inTags = new JSONArray();
        inTags.add(XrayTags.API);
        rule.put("inboundTag", inTags);
        rule.put("outboundTag", XrayTags.API);
        return rule;
    }

    /** 单 client rule: 命中其 inbound + email 时走对应 socks5 出站. */
    private JSONObject emailRoutingRule(XrayClientDO row) {
        JSONObject rule = new JSONObject();
        rule.put("type", "field");
        JSONArray inTags = new JSONArray();
        inTags.add(row.getExternalInboundRef());
        rule.put("inboundTag", inTags);
        JSONArray emails = new JSONArray();
        emails.add(row.getClientEmail());
        rule.put("user", emails);
        // outbound tag 的命名规则在 OutboundConfigReconciler, 这里调静态方法保证两端一致
        rule.put("outboundTag", OutboundConfigReconciler.userTagFor(row.getClientEmail()));
        return rule;
    }
}
