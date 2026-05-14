package com.nook.biz.node.handler.xray.client;

import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OperationContext;
import com.nook.biz.operation.api.spi.OperationHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * SERVER_RECONCILE handler; 定时器和手动入口都走这. 业务执行见 ClientOpExecutor.doReplayIfRestarted.
 *
 * @author nook
 */
@Component
public class ReconcileServerHandler implements OperationHandler {

    @Resource
    private ClientOpExecutor executor;

    @Override
    public String type() {
        return OpType.SERVER_RECONCILE.name();
    }

    @Override
    public Object execute(OperationContext ctx) {
        ctx.report("准备对账", 15);
        executor.doReplayIfRestarted(ctx.serverId(), ctx);
        return null;
    }
}
