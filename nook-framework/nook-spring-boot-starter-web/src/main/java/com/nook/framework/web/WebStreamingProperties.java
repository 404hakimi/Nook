package com.nook.framework.web;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Web 流式接口配置项
 *
 * @author nook
 */
@Data
@Component
@ConfigurationProperties(prefix = "nook.web.streaming")
public class WebStreamingProperties {

    /** SSE emitter 超时 = 操作超时 + 此缓冲; 流式接口最外层超时, 须 > 内层 (如 AgentControlClient HTTP) 缓冲; 跨洲远端操作留足余量. */
    private Duration emitterBuffer = Duration.ofSeconds(180);
}
