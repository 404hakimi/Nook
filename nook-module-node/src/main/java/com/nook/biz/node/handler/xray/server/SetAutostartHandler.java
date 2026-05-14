package com.nook.biz.node.handler.xray.server;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OpContext;
import com.nook.biz.operation.api.spi.OpHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * SERVER_AUTOSTART 的 handler; 入参 JSON 解 enabled flag. 业务执行见 ServerOpExecutor.doSetAutostart.
 *
 * @author nook
 */
@Component
public class SetAutostartHandler implements OpHandler {

    @Resource
    private ServerOpExecutor executor;

    @Override
    public String type() {
        return OpType.SERVER_AUTOSTART.name();
    }

    @Override
    public Object execute(OpContext ctx) {
        JSONObject params = JSON.parseObject(ctx.paramsJson());
        boolean enabled = params.getBooleanValue("enabled");
        ctx.report(enabled ? "开启开机自启" : "关闭开机自启", 30);
        return executor.doSetAutostart(ctx.serverId(), enabled, ctx);
    }
}
