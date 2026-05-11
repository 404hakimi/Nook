package com.nook.biz.operation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 各 opType 的执行超时 (yaml 前缀 nook.op).
 *
 * <p>修改超时不用动代码: 直接在 application.yml 的 nook.op.timeouts 下加/改条目即可.
 * Map key 为 biz 模块 OpType 枚举的 .name() 字符串, 不绑定枚举类.
 *
 * @author nook
 */
@Data
@ConfigurationProperties(prefix = "nook.op")
public class OpTimeoutProperties {

    /** 未在 timeouts 配置中显式声明的 op 用这个 */
    private Duration defaultTimeout = Duration.ofMinutes(2);

    /** 每个 opType 的独立超时 (key = OpType.name()) */
    private Map<String, Duration> timeouts = new HashMap<>();

    public Duration of(String opType) {
        Duration v = timeouts.get(opType);
        return v != null ? v : defaultTimeout;
    }
}
