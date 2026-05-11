package com.nook.biz.operation.internal;

import com.nook.biz.operation.api.OperationContext;
import com.nook.biz.operation.mapper.OpLogMapper;
import com.nook.biz.operation.persistence.OpLog;

/**
 * OperationContext 默认实现; 进度 report 直接写 op_log.
 *
 * <p>Stage 2 加 WebSocket 时, 这里同时推 WS sink, handler 代码不用改.
 *
 * @author nook
 */
class DefaultOperationContext implements OperationContext {

    private final OpLog op;
    private final OpLogMapper opLogMapper;

    DefaultOperationContext(OpLog op, OpLogMapper opLogMapper) {
        this.op = op;
        this.opLogMapper = opLogMapper;
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
    }
}
