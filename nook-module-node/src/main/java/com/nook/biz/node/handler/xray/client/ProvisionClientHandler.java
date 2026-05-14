package com.nook.biz.node.handler.xray.client;

import com.alibaba.fastjson2.JSON;
import com.nook.biz.node.controller.xray.vo.XrayClientProvisionReqVO;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OperationContext;
import com.nook.biz.operation.api.spi.OperationHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * CLIENT_PROVISION handler; 入参 JSON 反序列回 ClientProvisionReqVO. 业务执行见 ClientOpExecutor.doProvision.
 *
 * @author nook
 */
@Component
public class ProvisionClientHandler implements OperationHandler {

    @Resource
    private ClientOpExecutor executor;

    @Override
    public String type() {
        return OpType.CLIENT_PROVISION.name();
    }

    @Override
    public Object execute(OperationContext ctx) {
        XrayClientProvisionReqVO reqVO = JSON.parseObject(ctx.paramsJson(), XrayClientProvisionReqVO.class);
        ctx.report("入参校验", 10);
        return executor.doProvision(reqVO, ctx);
    }
}
