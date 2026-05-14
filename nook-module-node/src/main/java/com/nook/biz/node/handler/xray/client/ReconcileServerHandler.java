package com.nook.biz.node.handler.xray.client;

import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OpContext;
import com.nook.biz.operation.api.spi.OpHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * SERVER_RECONCILE handler; 定时器和手动入口都走这. 业务执行见 ClientOpExecutor.doReplayIfRestarted.
 *
 * @author nook
 */
@Component
public class ReconcileServerHandler implements OpHandler {

    @Resource
    private ClientOpExecutor executor;

    @Override
    public String type() {
        return OpType.SERVER_RECONCILE.name();
    }

    @Override
    public Object execute(OpContext ctx) {
        ctx.report("准备对账", 15);
        executor.doReplayIfRestarted(ctx.serverId(), ctx);
        return null;
    }
}
