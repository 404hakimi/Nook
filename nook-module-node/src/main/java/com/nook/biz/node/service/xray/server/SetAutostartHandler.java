package com.nook.biz.node.service.xray.server;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OperationContext;
import com.nook.biz.operation.api.spi.OperationHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * SERVER_AUTOSTART 的 handler; 入参 JSON 解 enabled flag.
 *
 * @author nook
 */
@Component
public class SetAutostartHandler implements OperationHandler {

    @Resource
    private XrayServerManageServiceImpl serviceImpl;

    @Override
    public String type() {
        return OpType.SERVER_AUTOSTART.name();
    }

    @Override
    public Object execute(OperationContext ctx) {
        JSONObject params = JSON.parseObject(ctx.paramsJson());
        boolean enabled = params.getBooleanValue("enabled");
        ctx.report(enabled ? "开启开机自启" : "关闭开机自启", 30);
        return serviceImpl.doSetAutostart(ctx.serverId(), enabled, ctx);
    }
}
