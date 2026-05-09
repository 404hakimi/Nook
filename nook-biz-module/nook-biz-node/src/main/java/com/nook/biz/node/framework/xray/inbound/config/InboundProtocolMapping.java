package com.nook.biz.node.framework.xray.inbound.config;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.google.protobuf.ByteString;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.xray.inbound.snapshot.InboundUserSpec;
import com.nook.common.web.exception.BusinessException;
import com.xray.common.serial.TypedMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/** Xray 协议映射 (vmess/vless/trojan/shadowsocks); 屏蔽各协议在 gRPC AddUser 与 xray.json 字段上的差异. */
@Getter
@RequiredArgsConstructor
public enum InboundProtocolMapping {

    VMESS("vmess") {
        @Override
        public TypedMessage buildAccountTypedMessage(InboundUserSpec spec) {
            com.xray.proxy.vmess.Account acct = com.xray.proxy.vmess.Account.newBuilder()
                    .setId(spec.uuid())
                    .setAlterId(0) // 现代 Xray vmess 走 AEAD, alterId 必须 0
                    .build();
            return wrap(com.xray.proxy.vmess.Account.getDescriptor().getFullName(), acct.toByteString());
        }

        @Override
        public JSONObject buildClientJson(InboundUserSpec spec) {
            JSONObject c = new JSONObject();
            c.put("id", spec.uuid());
            c.put("email", spec.email());
            c.put("alterId", 0);
            c.put("level", 0);
            return c;
        }
    },

    VLESS("vless") {
        @Override
        public TypedMessage buildAccountTypedMessage(InboundUserSpec spec) {
            com.xray.proxy.vless.Account acct = com.xray.proxy.vless.Account.newBuilder()
                    .setId(spec.uuid())
                    .setFlow(StrUtil.blankToDefault(spec.flow(), ""))
                    .setEncryption("none")
                    .build();
            return wrap(com.xray.proxy.vless.Account.getDescriptor().getFullName(), acct.toByteString());
        }

        @Override
        public JSONObject buildClientJson(InboundUserSpec spec) {
            JSONObject c = new JSONObject();
            c.put("id", spec.uuid());
            c.put("email", spec.email());
            if (StrUtil.isNotBlank(spec.flow())) c.put("flow", spec.flow());
            c.put("level", 0);
            return c;
        }
    },

    TROJAN("trojan") {
        @Override
        public TypedMessage buildAccountTypedMessage(InboundUserSpec spec) {
            // trojan 的密码字段我们复用 spec.uuid() 作为生成逻辑唯一来源
            com.xray.proxy.trojan.Account acct = com.xray.proxy.trojan.Account.newBuilder()
                    .setPassword(spec.uuid())
                    .build();
            return wrap(com.xray.proxy.trojan.Account.getDescriptor().getFullName(), acct.toByteString());
        }

        @Override
        public JSONObject buildClientJson(InboundUserSpec spec) {
            JSONObject c = new JSONObject();
            c.put("password", spec.uuid());
            c.put("email", spec.email());
            c.put("level", 0);
            return c;
        }
    };

    /** xray_inbound.protocol / spec.protocol 的字符串值, 全小写。 */
    private final String code;

    /** 序列化协议特定 Account 到 gRPC AddUserOperation 用的 TypedMessage。 */
    public abstract TypedMessage buildAccountTypedMessage(InboundUserSpec spec);

    /** 渲染 xray.json {@code inbounds[].settings.clients[]} 单条节点。 */
    public abstract JSONObject buildClientJson(InboundUserSpec spec);

    /** 按 code 解析; 未识别抛 BACKEND_OPERATION_FAILED, 上层把 serverId 与协议名带出去。 */
    public static InboundProtocolMapping of(String code) {
        return Arrays.stream(values())
                .filter(p -> StrUtil.equalsIgnoreCase(p.code, code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED,
                        "<unknown-protocol>", "不支持的协议: " + code));
    }

    private static TypedMessage wrap(String typeFullName, ByteString value) {
        return TypedMessage.newBuilder().setType(typeFullName).setValue(value).build();
    }
}
