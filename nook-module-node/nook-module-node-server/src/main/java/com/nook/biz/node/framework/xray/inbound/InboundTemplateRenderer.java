package com.nook.biz.node.framework.xray.inbound;

import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 入站协议模板渲染器; 把模板里的 ${} 占位符替换成 JSON 序列化后的值
 *
 * @author nook
 */
@Component
public class InboundTemplateRenderer {

    /**
     * 渲染模板: 每个 ${key} 替换成 vars[key] 的 JSON 序列化 (字符串带引号 / 数组 / 数字原样), 替换后即合法 JSON
     *
     * @param template 带 ${} 占位符的 JSON 模板
     * @param vars     占位符值 (key → 值)
     * @return 替换后的 inbound JSON
     */
    public String render(String template, Map<String, Object> vars) {
        String result = template;
        for (Map.Entry<String, Object> entry : vars.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = JSON.toJSONString(entry.getValue());
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
