package com.nook.biz.node.handler.xray.server;

import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OpContext;
import com.nook.biz.operation.api.spi.OpHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * XRAY_RESTART 的 handler 薄包装; 业务执行见 ServerOpExecutor.doRestart.
 *
 * @author nook
 */
@Component
public class RestartXrayHandler implements OpHandler {

    @Resource
    private ServerOpExecutor executor;

    @Override
    public String type() {
        return OpType.XRAY_RESTART.name();
    }

    @Override
    public Object execute(OpContext ctx) {
        ctx.report("建立 SSH 会话", 15);
        // executor 内部会用 sink 报 systemctl 状态
        return executor.doRestart(ctx.serverId(), ctx);
    }
}
