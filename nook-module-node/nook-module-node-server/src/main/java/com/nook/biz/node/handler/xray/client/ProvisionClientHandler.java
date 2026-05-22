package com.nook.biz.node.handler.xray.client;

import com.alibaba.fastjson2.JSON;
import com.nook.biz.node.controller.xray.vo.XrayClientProvisionReqVO;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OpContext;
import com.nook.biz.operation.api.spi.OpHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * CLIENT_PROVISION handler; 入参 JSON 反序列回 ClientProvisionReqVO. 业务执行见 ClientOpExecutor.doProvision.
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class ProvisionClientHandler implements OpHandler {

    private final ClientOpExecutor executor;

    @Override
    public String type() {
        return OpType.CLIENT_PROVISION.name();
    }

    @Override
    public Object execute(OpContext ctx) {
        XrayClientProvisionReqVO reqVO = JSON.parseObject(ctx.paramsJson(), XrayClientProvisionReqVO.class);
        ctx.report("入参校验", 10);
        return executor.doProvision(reqVO, ctx);
    }
}
