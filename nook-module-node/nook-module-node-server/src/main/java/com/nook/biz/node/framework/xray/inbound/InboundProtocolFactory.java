package com.nook.biz.node.framework.xray.inbound;

import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 入站协议工厂; 启动时按各实现声明的 supportedForms 建「协议名 → 实现」分派表
 *
 * @author nook
 */
@Component
public class InboundProtocolFactory {

    @Resource
    private List<InboundProtocol> protocols;

    /** 协议名 (vmess / vless) → 实现; 协议名只来自 XrayInboundProtocolEnum, 实现里无字面量. */
    private final Map<String, InboundProtocol> byProtocol = new HashMap<>();

    @PostConstruct
    private void init() {
        for (InboundProtocol p : protocols) {
            for (XrayInboundProtocolEnum form : p.supportedForms()) {
                byProtocol.put(form.getProtocol(), p);
            }
        }
    }

    /**
     * 装机侧: 按入站配置规格选协议实现
     *
     * @param spec 入站配置规格
     * @return 协议实现
     */
    public InboundProtocol resolve(InboundSetupSpec spec) {
        return resolveByProtocol(spec.getProtocol());
    }

    /**
     * 对账侧: 按基础协议名 (protocol_key 解出) 选协议实现; 无匹配抛错
     *
     * @param protocol 基础协议名 (vmess / vless)
     * @return 协议实现
     */
    public InboundProtocol resolveByProtocol(String protocol) {
        InboundProtocol p = protocol == null ? null : byProtocol.get(protocol.toLowerCase());
        if (p == null) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID, "不支持的协议形态: " + protocol);
        }
        return p;
    }

    /** 全部协议实现 (每个实现 = 一个协议家族, 如 vmess 含 ws-tls/ws-plain); 装机表单 schema 列举用. */
    public List<InboundProtocol> all() {
        return protocols;
    }
}
