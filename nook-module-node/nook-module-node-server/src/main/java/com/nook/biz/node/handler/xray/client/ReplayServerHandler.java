package com.nook.biz.node.handler.xray.client;

import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OpContext;
import com.nook.biz.operation.api.spi.OpHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * CLIENT_ALL_SYNC handler; 委托 ClientOpExecutor.doReplayServer
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class ReplayServerHandler implements OpHandler {

    private final ClientOpExecutor executor;

    @Override
    public String type() {
        return OpType.CLIENT_ALL_SYNC.name();
    }

    @Override
    public Object execute(OpContext ctx) {
        ctx.report("准备同步全部客户端", 10);
        return executor.doReplayServer(ctx.serverId(), ctx);
    }
}
