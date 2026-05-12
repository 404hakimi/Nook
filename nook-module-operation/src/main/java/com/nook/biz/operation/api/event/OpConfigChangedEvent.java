package com.nook.biz.operation.api.event;

/**
 * op_config 行变更事件; OpConfigResolver 据此清缓存让下次取走 DB 新值
 *
 * @param opType 被改的 OpType.name(); null 表示批量变更, 清空整张缓存
 *
 * @author nook
 */
public record OpConfigChangedEvent(String opType) {
}
