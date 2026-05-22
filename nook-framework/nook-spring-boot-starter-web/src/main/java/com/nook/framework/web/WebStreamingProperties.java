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

    private Duration emitterBuffer = Duration.ofSeconds(60);
}
