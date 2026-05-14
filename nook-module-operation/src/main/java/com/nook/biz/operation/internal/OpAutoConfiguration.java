package com.nook.biz.operation.internal;

import com.nook.biz.operation.api.spi.OpConfigResolver;
import com.nook.biz.operation.api.spi.OpHandler;
import com.nook.biz.operation.api.spi.OpOrchestrator;
import com.nook.biz.operation.config.OpOrchestratorProperties;
import com.nook.biz.operation.dal.mysql.mapper.OpConfigMapper;
import com.nook.biz.operation.dal.mysql.mapper.OpLogMapper;
import com.nook.biz.operation.internal.orchestrator.DefaultOpOrchestrator;
import com.nook.biz.operation.internal.orchestrator.OpHandlerRegistry;
import com.nook.biz.operation.internal.orchestrator.OpWatchdog;
import com.nook.biz.operation.internal.progress.ws.OpProgressHub;
import com.nook.biz.operation.internal.resolver.DefaultOpConfigResolver;
import com.nook.biz.operation.internal.startup.OpStartupCleaner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * nook-biz-operation 自动装配; 由 META-INF/spring/.../AutoConfiguration.imports 引导.
 *
 * @author nook
 */
@AutoConfiguration
@EnableConfigurationProperties({OpOrchestratorProperties.class})
public class OpAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "opWorkerPool")
    public ExecutorService opWorkerPool(OpOrchestratorProperties props) {
        return Executors.newFixedThreadPool(props.getWorkerPoolSize(), namedDaemon("op-worker"));
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "opWatchdogScheduler")
    public ScheduledExecutorService opWatchdogScheduler(OpOrchestratorProperties props) {
        return Executors.newScheduledThreadPool(props.getWatchdogPoolSize(), namedDaemon("op-watchdog"));
    }

    @Bean
    @ConditionalOnMissingBean
    public OpHandlerRegistry opHandlerRegistry(List<OpHandler> handlers) {
        return new OpHandlerRegistry(handlers);
    }

    /** Watchdog 默认无 interrupter; 业务模块可声明 TimeoutInterrupter bean 覆盖. */
    @Bean
    @ConditionalOnMissingBean
    public OpWatchdog opWatchdog(ScheduledExecutorService opWatchdogScheduler,
                                 OpLogMapper opLogMapper,
                                 @Autowired(required = false) OpWatchdog.TimeoutInterrupter interrupter,
                                 OpProgressHub opProgressHub) {
        return new OpWatchdog(opWatchdogScheduler, opLogMapper, interrupter, opProgressHub);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpConfigResolver opConfigResolver(OpConfigMapper opConfigMapper) {
        return new DefaultOpConfigResolver(opConfigMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpOrchestrator opOrchestrator(OpLogMapper opLogMapper,
                                         OpHandlerRegistry opHandlerRegistry,
                                         OpConfigResolver opConfigResolver,
                                         OpWatchdog opWatchdog,
                                         ExecutorService opWorkerPool,
                                         OpProgressHub opProgressHub) {
        return new DefaultOpOrchestrator(opLogMapper, opHandlerRegistry, opConfigResolver,
                opWatchdog, opWorkerPool, opProgressHub);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpStartupCleaner opStartupCleaner(OpLogMapper opLogMapper) {
        return new OpStartupCleaner(opLogMapper);
    }

    private static ThreadFactory namedDaemon(String prefix) {
        AtomicInteger seq = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
