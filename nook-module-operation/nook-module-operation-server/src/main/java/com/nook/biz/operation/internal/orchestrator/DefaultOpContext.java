package com.nook.biz.operation.internal.orchestrator;

import com.nook.biz.operation.api.event.OpProgressEvent;
import com.nook.biz.operation.api.OpStatus;
import com.nook.biz.operation.api.spi.OpContext;
import com.nook.biz.operation.internal.progress.ws.OpProgressHub;
import com.nook.biz.operation.dal.mysql.mapper.OpLogMapper;
import com.nook.biz.operation.dal.dataobject.OpLogDO;

/**
 * OpContext 默认实现; handler 一次 report 同时写 DB 进度 + 推 WebSocket 给前端订阅者.
 *
 * @author nook
 */
class DefaultOpContext implements OpContext {

    private final OpLogDO op;
    private final OpLogMapper opLogMapper;
    private final OpProgressHub hub;

    DefaultOpContext(OpLogDO op, OpLogMapper opLogMapper, OpProgressHub hub) {
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
