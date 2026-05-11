package com.nook.biz.node.service.xray.client;

import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.OperationContext;
import com.nook.biz.operation.api.OperationHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * SERVER_RECONCILE handler; 定时器和手动入口都走这.
 *
 * @author nook
 */
@Component
public class ReconcileServerHandler implements OperationHandler {

    @Resource
    private XrayClientServiceImpl serviceImpl;

    @Override
    public String type() {
        return OpType.SERVER_RECONCILE.name();
    }

    @Override
    public Object execute(OperationContext ctx) {
        ctx.report("准备对账", 15);
        serviceImpl.doReplayIfRestarted(ctx.serverId(), ctx);
        return null;
    }
}
