package com.nook.biz.node.handler.xray.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OpContext;
import com.nook.biz.operation.api.spi.OpHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * CLIENT_RESET_TRAFFIC handler; 委托 ClientOpExecutor.doResetTraffic
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class ResetTrafficClientHandler implements OpHandler {

    private final ClientOpExecutor executor;

    @Override
    public String type() {
        return OpType.CLIENT_RESET_TRAFFIC.name();
    }

    @Override
    public Object execute(OpContext ctx) {
        JSONObject params = JSON.parseObject(ctx.paramsJson());
        String clientId = params.getString("clientId");
        ctx.report("入队 reset-traffic", 10);
        executor.doResetTraffic(clientId, ctx);
        return null;
    }
}
