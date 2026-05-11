package com.nook.biz.node.service.xray.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.OperationContext;
import com.nook.biz.operation.api.OperationHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * CLIENT_REVOKE handler.
 *
 * @author nook
 */
@Component
public class RevokeClientHandler implements OperationHandler {

    @Resource
    private XrayClientServiceImpl serviceImpl;

    @Override
    public String type() {
        return OpType.CLIENT_REVOKE.name();
    }

    @Override
    public Object execute(OperationContext ctx) {
        JSONObject params = JSON.parseObject(ctx.paramsJson());
        String clientId = params.getString("clientId");
        ctx.report("正在吊销", 30);
        serviceImpl.doRevoke(clientId);
        return null;
    }
}
