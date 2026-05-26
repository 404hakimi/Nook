package com.nook.biz.node.handler.xray.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OpContext;
import com.nook.biz.operation.api.spi.OpHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * CLIENT_SYNC handler; 委托 ClientOpExecutor.doSyncOne
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class SyncClientHandler implements OpHandler {

    private final ClientOpExecutor executor;

    @Override
    public String type() {
        return OpType.CLIENT_SYNC.name();
    }

    @Override
    public Object execute(OpContext ctx) {
        JSONObject params = JSON.parseObject(ctx.paramsJson());
        String clientId = params.getString("clientId");
        ctx.report("校验客户端", 10);
        executor.doSyncOne(clientId, ctx);
        return null;
    }
}
