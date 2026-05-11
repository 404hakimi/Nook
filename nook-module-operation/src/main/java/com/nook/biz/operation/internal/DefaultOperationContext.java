package com.nook.biz.operation.internal;

import com.nook.biz.operation.api.OpProgressEvent;
import com.nook.biz.operation.api.OpStatus;
import com.nook.biz.operation.api.OperationContext;
import com.nook.biz.operation.internal.ws.OpProgressHub;
import com.nook.biz.operation.mapper.OpLogMapper;
import com.nook.biz.operation.persistence.OpLog;

/**
 * OperationContext 默认实现; 进度 report 同时写 DB + 推 WS.
 *
 * <p>之前只写 DB, 前端只能轮询; 加 hub.broadcast 后 handler 调一次 ctx.report 立即推到订阅者.
 *
 * @author nook
 */
class DefaultOperationContext implements OperationContext {

    private final OpLog op;
    private final OpLogMapper opLogMapper;
    private final OpProgressHub hub;

    DefaultOperationContext(OpLog op, OpLogMapper opLogMapper, OpProgressHub hub) {
        this.op = op;
        this.opLogMapper = opLogMapper;
        this.hub = hub;
    }

    @Override
    public String opId() {
        return op.getId();
    }

    @Override
    public String serverId() {
        return op.getServerId();
    }

    @Override
    public String targetId() {
        return op.getTargetId();
    }

    @Override
    public String paramsJson() {
        return op.getParamsJson();
    }

    @Override
    public void report(String step, int progressPct) {
        report(step, progressPct, null);
    }

    @Override
    public void report(String step, int progressPct, String message) {
        int pct = Math.max(0, Math.min(100, progressPct));
        opLogMapper.updateProgress(op.getId(), step, pct, message);
        if (hub != null) {
            hub.broadcast(OpProgressEvent.builder()
                    .opId(op.getId())
                    .serverId(op.getServerId())
                    .opType(op.getOpType())
                    .status(OpStatus.RUNNING)
                    .currentStep(step)
                    .progressPct(pct)
                    .message(message)
                    .timestamp(System.currentTimeMillis())
                    .build());
        }
    }
}
