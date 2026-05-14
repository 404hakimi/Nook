package com.nook.biz.operation.internal.orchestrator;

import com.nook.common.web.exception.BusinessException;
import com.nook.biz.operation.enums.OpErrorCode;
import com.nook.biz.operation.api.spi.OpHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * opType → Handler 的查找表; 启动时由 Spring 把所有 OpHandler bean 注入到这里.
 *
 * <p>同一 opType 注册多个 handler 直接抛错, 避免业务侧不小心写两个; 找不到也抛, 不静默忽略.
 *
 * @author nook
 */
public class OpHandlerRegistry {

    private final Map<String, OpHandler> handlers = new HashMap<>();

    public OpHandlerRegistry(List<OpHandler> beans) {
        for (OpHandler h : beans) {
            String type = h.type();
            OpHandler prev = handlers.put(type, h);
            if (prev != null) {
                throw new IllegalStateException("opType " + type + " 注册了多个 handler: "
                        + prev.getClass().getName() + " / " + h.getClass().getName());
            }
        }
    }

    OpHandler resolve(String type) {
        OpHandler h = handlers.get(type);
        if (h == null) {
            throw new BusinessException(OpErrorCode.HANDLER_NOT_FOUND, type);
        }
        return h;
    }
}
