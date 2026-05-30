package com.nook.biz.trade.event;

import com.nook.biz.trade.service.TradeSubscriptionChangeLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 订阅换机事件监听器; 把 {@link SubscriptionMachineChangeEvent} 落成换机历史日志.
 * 落库失败只告警不抛出 (开通/换机本身已完成, 不能因丢一条审计而回退主流程).
 *
 * @author nook
 */
@Slf4j
@Component
public class SubscriptionMachineChangeListener {

    @Resource
    private TradeSubscriptionChangeLogService changeLogService;

    @EventListener
    public void onMachineChange(SubscriptionMachineChangeEvent event) {
        try {
            changeLogService.record(event);
        } catch (Exception e) {
            log.warn("[换机日志] 落库失败 sub={} type={} reason={}: {}",
                    event.getSubscriptionId(), event.getChangeType(), event.getReason(), e.getMessage());
        }
    }
}
