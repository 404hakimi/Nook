package com.nook.biz.operation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Orchestrator 运行参数 (yaml 前缀 nook.op.orchestrator).
 *
 * @author nook
 */
@Data
@ConfigurationProperties(prefix = "nook.op.orchestrator")
public class OpOrchestratorProperties {

    /** worker 线程池大小 (== 同一时刻最多可活跃的 server 数) */
    private int workerPoolSize = 32;

    /** watchdog 调度线程数; 1 个够用, 仅起定时回调不跑业务 */
    private int watchdogPoolSize = 2;

    /** 队列 offer 阻塞超时 (理论不会触发, LinkedBlockingQueue 无界); 保留以防换成 bounded 队列 */
    private Duration enqueueOfferTimeout = Duration.ofSeconds(1);
}
