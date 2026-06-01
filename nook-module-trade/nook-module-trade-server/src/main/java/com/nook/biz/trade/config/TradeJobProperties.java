package com.nook.biz.trade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * trade 模块定时任务配置 (映射 yml nook.trade.job.*)
 *
 * <p>用 @Component 注册以获得稳定 bean 名 tradeJobProperties, 供 @Scheduled 的
 * SpEL "#{@tradeJobProperties.xxx}" 引用 cron.
 *
 * @author nook
 */
@Data
@Component
@ConfigurationProperties(prefix = "nook.trade.job")
public class TradeJobProperties {

    /** 订阅生命周期巡检 cron (到期释放 + 流量耗尽停服). */
    private String lifecycleCron;

    /** 线路机故障切换. */
    private Failover failover = new Failover();

    @Data
    public static class Failover {
        /** 总开关; 默认关, 确认无误再开. */
        private boolean enabled;
        /** 巡检 cron. */
        private String cron;
        /** THROTTLED(到顶) 每轮每机迁移条数上限; OFFLINE 不限. */
        private int throttledBatch;
    }
}
