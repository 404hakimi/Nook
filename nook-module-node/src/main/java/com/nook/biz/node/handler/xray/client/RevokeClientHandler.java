package com.nook.biz.node.handler.xray.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OpContext;
import com.nook.biz.operation.api.spi.OpHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * CLIENT_REVOKE handler; 业务执行见 ClientOpExecutor.doRevoke.
 *
 * @author nook
 */
@Component
public class RevokeClientHandler implements OpHandler {

    @Resource
    private ClientOpExecutor executor;

    @Override
    public String type() {
        return OpType.CLIENT_REVOKE.name();
    }

    @Override
    public Object execute(OpContext ctx) {
        JSONObject params = JSON.parseObject(ctx.paramsJson());
        String clientId = params.getString("clientId");
        ctx.report("加载客户端记录", 15);
        executor.doRevoke(clientId, ctx);
        return null;
    }
}
