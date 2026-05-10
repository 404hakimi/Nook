package com.nook.biz.node.framework.xray.inbound.config;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;
import com.nook.common.web.exception.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * Xray 协议映射 (vless / vmess / trojan); 屏蔽各协议在 xray.json clients[] 字段上的差异; shadowsocks 暂不支持.
 *
 * <p>原 gRPC TypedMessage 渲染 (buildAccountTypedMessage) 已删, 走 SSH+CLI 后只用 JSON 渲染.
 *
 * @author nook
 */
@Getter
@RequiredArgsConstructor
public enum InboundProtocolMapping {

    VMESS("vmess") {
        @Override
        public JSONObject buildClientJson(InboundUserSpec spec) {
            JSONObject c = new JSONObject();
            c.put("id", spec.getUuid());
            c.put("email", spec.getEmail());
            c.put("alterId", 0);
            c.put("level", 0);
            return c;
        }
    },

    VLESS("vless") {
        @Override
        public JSONObject buildClientJson(InboundUserSpec spec) {
            JSONObject c = new JSONObject();
            c.put("id", spec.getUuid());
            c.put("email", spec.getEmail());
            if (StrUtil.isNotBlank(spec.getFlow())) c.put("flow", spec.getFlow());
            c.put("level", 0);
            return c;
        }
    },

    TROJAN("trojan") {
        @Override
        public JSONObject buildClientJson(InboundUserSpec spec) {
            JSONObject c = new JSONObject();
            c.put("password", spec.getUuid());
            c.put("email", spec.getEmail());
            c.put("level", 0);
            return c;
        }
    };

    /** xray_inbound.protocol / spec.protocol 的字符串值, 全小写. */
    private final String code;

    /**
     * 渲染 xray.json inbounds[].settings.clients[] 单条节点.
     *
     * @param spec user 协议规格
     * @return JSONObject
     */
    public abstract JSONObject buildClientJson(InboundUserSpec spec);

    /**
     * 按 code 解析协议, 未识别抛 BACKEND_OPERATION_FAILED.
     *
     * @param code 协议名 (大小写不敏感)
     * @return InboundProtocolMapping
     */
    public static InboundProtocolMapping of(String code) {
        return Arrays.stream(values())
                .filter(p -> StrUtil.equalsIgnoreCase(p.code, code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                        "<unknown-protocol>", "不支持的协议: " + code));
    }
}
