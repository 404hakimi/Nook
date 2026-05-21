package com.nook.biz.node.handler.xray.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OpContext;
import com.nook.biz.operation.api.spi.OpHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * CLIENT_ROTATE handler; 业务执行见 ClientOpExecutor.doRotate.
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class RotateClientHandler implements OpHandler {

    private final ClientOpExecutor executor;

    @Override
    public String type() {
        return OpType.CLIENT_ROTATE.name();
    }

    @Override
    public Object execute(OpContext ctx) {
        JSONObject params = JSON.parseObject(ctx.paramsJson());
        String clientId = params.getString("clientId");
        ctx.report("加载客户端记录", 15);
        return executor.doRotate(clientId, ctx);
    }
}
