package com.nook.biz.operation.internal;

import com.nook.biz.operation.api.spi.OpConfigResolver;
import com.nook.biz.operation.api.spi.OperationHandler;
import com.nook.biz.operation.api.spi.OperationOrchestrator;
import com.nook.biz.operation.config.OpOrchestratorProperties;
import com.nook.biz.operation.dal.mysql.mapper.OpConfigMapper;
import com.nook.biz.operation.dal.mysql.mapper.OpLogMapper;
import com.nook.biz.operation.internal.orchestrator.DefaultOperationOrchestrator;
import com.nook.biz.operation.internal.orchestrator.HandlerRegistry;
import com.nook.biz.operation.internal.orchestrator.OpWatchdog;
import com.nook.biz.operation.internal.progress.ws.OpProgressHub;
import com.nook.biz.operation.internal.resolver.DefaultOpConfigResolver;
import com.nook.biz.operation.internal.startup.OpStartupCleaner;
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
 * nook-biz-operation 自动装配; 由 META-INF/spring/.../AutoConfiguration.imports 引导
 *
 * @author nook
 */
@AutoConfiguration
@EnableConfigurationProperties({OpOrchestratorProperties.class})
public class OperationAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "operationWorkerPool")
    public ExecutorService operationWorkerPool(OpOrchestratorProperties props) {
        return Executors.newFixedThreadPool(props.getWorkerPoolSize(),
                namedDaemon("op-worker"));
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "operationWatchdogScheduler")
    public ScheduledExecutorService operationWatchdogScheduler(OpOrchestratorProperties props) {
        return Executors.newScheduledThreadPool(props.getWatchdogPoolSize(),
                namedDaemon("op-watchdog"));
    }

    @Bean
    @ConditionalOnMissingBean
    public HandlerRegistry operationHandlerRegistry(List<OperationHandler> handlers) {
        return new HandlerRegistry(handlers);
    }

    /**
     * Watchdog 默认无 interrupter; 业务模块 (如 nook-biz-node) 可声明自己的 TimeoutInterrupter bean 覆盖.
     */
    @Bean
    @ConditionalOnMissingBean
    public OpWatchdog operationWatchdog(ScheduledExecutorService operationWatchdogScheduler,
                                        OpLogMapper opLogMapper,
                                        @org.springframework.beans.factory.annotation.Autowired(required = false)
                                        OpWatchdog.TimeoutInterrupter interrupter,
                                        OpProgressHub opProgressHub) {
        return new OpWatchdog(operationWatchdogScheduler, opLogMapper, interrupter, opProgressHub);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpConfigResolver opConfigResolver(OpConfigMapper opConfigMapper) {
        return new DefaultOpConfigResolver(opConfigMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationOrchestrator operationOrchestrator(OpLogMapper opLogMapper,
                                                       HandlerRegistry operationHandlerRegistry,
                                                       OpConfigResolver opConfigResolver,
                                                       OpWatchdog operationWatchdog,
                                                       ExecutorService operationWorkerPool,
                                                       OpProgressHub opProgressHub) {
        return new DefaultOperationOrchestrator(opLogMapper, operationHandlerRegistry,
                opConfigResolver, operationWatchdog, operationWorkerPool, opProgressHub);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpStartupCleaner operationStartupCleaner(OpLogMapper opLogMapper) {
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
