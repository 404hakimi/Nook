package com.nook.biz.node.service.xray.server;

import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.OperationContext;
import com.nook.biz.operation.api.OperationHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * XRAY_RESTART 的 handler 薄包装; 真正 recipe 在 XrayServerManageServiceImpl.doRestart.
 *
 * @author nook
 */
@Component
public class RestartXrayHandler implements OperationHandler {

    @Resource
    private XrayServerManageServiceImpl serviceImpl;

    @Override
    public String type() {
        return OpType.XRAY_RESTART.name();
    }

    @Override
    public Object execute(OperationContext ctx) {
        ctx.report("建立 SSH 会话", 15);
        // service 内部会用 sink 报 systemctl 状态
        return serviceImpl.doRestart(ctx.serverId(), ctx);
    }
}
