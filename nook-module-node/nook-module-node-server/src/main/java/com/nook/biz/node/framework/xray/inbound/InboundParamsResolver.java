package com.nook.biz.node.framework.xray.inbound;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.api.enums.XrayInboundProtocolEnum;
import com.nook.biz.node.framework.xray.inbound.vless.VlessRealityParams;
import com.nook.biz.node.framework.xray.inbound.vmess.VmessWsParams;
import com.nook.common.web.exception.BusinessException;

/**
 * inbound 语义参数还原器; 按 protocol_key 把 DB params JSON 反序列化成对应协议子类
 *
 * @author nook
 */
public final class InboundParamsResolver {

    private InboundParamsResolver() {
    }

    /**
     * 按协议形态把 params JSON 还原成对应协议子类
     *
     * @param protocolKey 协议形态 key
     * @param json        DB params 列 JSON; 空返 null
     * @return 对应协议的 params 子类; json 空时 null
     */
    public static InboundParams resolve(String protocolKey, String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        XrayInboundProtocolEnum proto = XrayInboundProtocolEnum.fromKey(protocolKey);
        if (proto == null) {
            throw new BusinessException(XrayErrorCode.SERVER_INSTALL_INVALID, "未知协议形态 " + protocolKey);
        }
        return JSON.parseObject(json, classOf(proto));
    }

    /** 协议形态 → 对应 params 子类; 穷举不写 default, 加协议时编译器提醒补. */
    private static Class<? extends InboundParams> classOf(XrayInboundProtocolEnum proto) {
        return switch (proto) {
            case VMESS_WS_TLS, VMESS_WS_PLAIN -> VmessWsParams.class;
            case VLESS_REALITY -> VlessRealityParams.class;
        };
    }
}
