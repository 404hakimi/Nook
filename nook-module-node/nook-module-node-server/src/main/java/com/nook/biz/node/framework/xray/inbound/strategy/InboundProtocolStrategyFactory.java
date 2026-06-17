package com.nook.biz.node.framework.xray.inbound.strategy;

import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 入站协议策略工厂; 按装机请求选匹配的策略
 *
 * @author nook
 */
@Component
public class InboundProtocolStrategyFactory {

    @Resource
    private List<InboundProtocolStrategy> strategies;

    /**
     * 选匹配的协议策略; 无匹配抛错
     *
     * @param reqVO 装机入参
     * @return 协议策略
     */
    public InboundProtocolStrategy resolve(XrayInstallReqVO reqVO) {
        return strategies.stream()
                .filter(s -> s.supports(reqVO))
                .findFirst()
                .orElseThrow(() -> new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID,
                        "不支持的协议形态: " + reqVO.getProtocol()));
    }
}
