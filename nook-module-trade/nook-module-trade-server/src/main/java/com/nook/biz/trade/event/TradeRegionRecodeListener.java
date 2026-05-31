package com.nook.biz.trade.event;

import com.nook.biz.system.api.region.event.RegionRecodedEvent;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 区域码更正监听: 把套餐 (trade_plan.region_code) 的旧码迁到新码.
 *
 * <p>同步监听, 与 system 发布方同一事务; 抛异常即整体回滚.
 *
 * @author nook
 */
@Slf4j
@Component
public class TradeRegionRecodeListener {

    @Resource
    private TradePlanMapper tradePlanMapper;

    @EventListener
    public void onRecode(RegionRecodedEvent e) {
        int n = tradePlanMapper.migrateRegionCode(e.getOldCode(), e.getNewCode());
        log.info("[区域更正] {} → {}: 迁移 {} 个套餐", e.getOldCode(), e.getNewCode(), n);
    }
}
