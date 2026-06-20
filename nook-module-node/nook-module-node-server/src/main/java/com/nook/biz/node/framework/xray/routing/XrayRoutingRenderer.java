package com.nook.biz.node.framework.xray.routing;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Xray 路由规则 (adrules) 入参渲染器; 按 email → outbound 绑定, 协议无关
 *
 * @author nook
 */
@Component
public class XrayRoutingRenderer {

    /**
     * 渲染 adrules 入参 JSON: {"routing":{"rules":[{ruleTag,user,outboundTag}]}}
     *
     * @param ruleTag     规则 tag
     * @param userEmails  命中该规则的 user email 列表
     * @param outboundTag 流量导向的出站 tag
     * @return adrules 入参 JSON
     */
    public String addRuleJson(String ruleTag, List<String> userEmails, String outboundTag) {
        JSONArray users = new JSONArray();
        users.addAll(userEmails);
        JSONObject rule = new JSONObject();
        rule.put("ruleTag", ruleTag);
        rule.put("user", users);
        rule.put("outboundTag", outboundTag);
        JSONArray rules = new JSONArray();
        rules.add(rule);
        JSONObject routing = new JSONObject();
        routing.put("rules", rules);
        JSONObject config = new JSONObject();
        config.put("routing", routing);
        return config.toJSONString();
    }
}
