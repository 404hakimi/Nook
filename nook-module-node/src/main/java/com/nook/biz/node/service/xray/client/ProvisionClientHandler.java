package com.nook.biz.node.service.xray.client;

import com.alibaba.fastjson2.JSON;
import com.nook.biz.node.controller.xray.client.vo.ClientProvisionReqVO;
import com.nook.biz.operation.api.OpType;
import com.nook.biz.operation.api.spi.OperationContext;
import com.nook.biz.operation.api.spi.OperationHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * CLIENT_PROVISION handler; 入参 JSON 反序列回 ClientProvisionReqVO. 进度细分见 service.doProvision.
 *
 * @author nook
 */
@Component
public class ProvisionClientHandler implements OperationHandler {

    @Resource
    private XrayClientServiceImpl serviceImpl;

    @Override
    public String type() {
        return OpType.CLIENT_PROVISION.name();
    }

    @Override
    public Object execute(OperationContext ctx) {
        ClientProvisionReqVO reqVO = JSON.parseObject(ctx.paramsJson(), ClientProvisionReqVO.class);
        ctx.report("入参校验", 10);
        return serviceImpl.doProvision(reqVO, ctx);
    }
}
