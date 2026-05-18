package com.nook.biz.node.handler.xray.client;

import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OpContext;
import com.nook.biz.operation.api.spi.OpHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * CLIENT_RECONCILE handler (客户端自检对账); 定时调度入口, 探 xray 重启则触发全量同步.
 * 业务执行见 ClientOpExecutor.doReplayIfRestarted.
 *
 * @author nook
 */
@Component
public class ReconcileServerHandler implements OpHandler {

    @Resource
    private ClientOpExecutor executor;

    @Override
    public String type() {
        return OpType.CLIENT_RECONCILE.name();
    }

    @Override
    public Object execute(OpContext ctx) {
        ctx.report("准备对账", 15);
        executor.doReplayIfRestarted(ctx.serverId(), ctx);
        return null;
    }
}
