package com.nook.biz.operation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 各 opType 的执行 / 等待超时 (yaml 前缀 nook.op)
 *
 * <p>Map key = OpType.name() 字符串, 不绑业务模块的枚举类.
 *
 * @author nook
 */
@Data
@ConfigurationProperties(prefix = "nook.op")
public class OpTimeoutProperties {

    // handler.execute() 跑多久切线程; 由 watchdog 强制
    private Map<String, Duration> timeouts = new HashMap<>();

    // submitAndWait 的 caller 阻塞总时长 (= 排队 + 执行); 缺省 fallback 到 exec + queue-grace
    private Map<String, Duration> waitTimeouts = new HashMap<>();

    // exec timeout 缺省值; timeouts map 未声明该 opType 时用此值
    private Duration defaultTimeout = Duration.ofMinutes(2);

    // wait 没单独配时, queue 预留宽限期 (caller wait = exec + queueGracePeriod)
    private Duration queueGracePeriod = Duration.ofSeconds(30);

    /** 取 handler 执行超时 (watchdog 用). */
    public Duration execOf(String opType) {
        Duration v = timeouts.get(opType);
        return v != null ? v : defaultTimeout;
    }

    /** 取 caller 阻塞等待超时 (submitAndWait 用); 没单独配时按 exec + grace 派生. */
    public Duration waitOf(String opType) {
        Duration v = waitTimeouts.get(opType);
        return v != null ? v : execOf(opType).plus(queueGracePeriod);
    }

    /** @deprecated 老 API; 调用方应换 execOf / waitOf 明确语义 */
    @Deprecated
    public Duration of(String opType) {
        return execOf(opType);
    }
}
