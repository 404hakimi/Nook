package com.nook.biz.node.framework.xray.inbound.protocol;

import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 入站协议工厂; 按装机请求选匹配的协议实现
 *
 * @author nook
 */
@Component
public class InboundProtocolFactory {

    @Resource
    private List<InboundProtocol> protocols;

    /**
     * 装机侧: 按装机请求选协议实现
     *
     * @param reqVO 装机入参
     * @return 协议实现
     */
    public InboundProtocol resolve(XrayInstallReqVO reqVO) {
        return resolveByProtocol(reqVO.getInbound().getProtocol());
    }

    /**
     * 对账侧: 按基础协议名 (protocol_key 解出) 选协议实现; 无匹配抛错
     *
     * @param protocol 基础协议名 (vmess / vless)
     * @return 协议实现
     */
    public InboundProtocol resolveByProtocol(String protocol) {
        return protocols.stream()
                .filter(p -> p.supports(protocol))
                .findFirst()
                .orElseThrow(() -> new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID,
                        "不支持的协议形态: " + protocol));
    }
}
