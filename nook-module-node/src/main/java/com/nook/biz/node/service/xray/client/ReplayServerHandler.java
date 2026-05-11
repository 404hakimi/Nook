package com.nook.biz.node.service.xray.client;

import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.OperationContext;
import com.nook.biz.operation.api.OperationHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * SERVER_REPLAY handler.
 *
 * @author nook
 */
@Component
public class ReplayServerHandler implements OperationHandler {

    @Resource
    private XrayClientServiceImpl serviceImpl;

    @Override
    public String type() {
        return OpType.SERVER_REPLAY.name();
    }

    @Override
    public Object execute(OperationContext ctx) {
        ctx.report("准备 replay", 10);
        return serviceImpl.doReplayServer(ctx.serverId(), ctx);
    }
}
