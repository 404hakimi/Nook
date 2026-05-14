package com.nook.biz.node.handler.xray.client;

import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OpContext;
import com.nook.biz.operation.api.spi.OpHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * SERVER_REPLAY handler; 业务执行见 ClientOpExecutor.doReplayServer.
 *
 * @author nook
 */
@Component
public class ReplayServerHandler implements OpHandler {

    @Resource
    private ClientOpExecutor executor;

    @Override
    public String type() {
        return OpType.SERVER_REPLAY.name();
    }

    @Override
    public Object execute(OpContext ctx) {
        ctx.report("准备 replay", 10);
        return executor.doReplayServer(ctx.serverId(), ctx);
    }
}
