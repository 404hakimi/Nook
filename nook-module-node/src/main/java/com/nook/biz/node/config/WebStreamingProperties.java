package com.nook.biz.node.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 流式接口参数 (yaml 前缀 nook.node.streaming)
 *
 * @author nook
 */
@Data
@ConfigurationProperties(prefix = "nook.node.streaming")
public class WebStreamingProperties {

    // ResponseBodyEmitter 端比 install/op 端多留的余量, 留给 service 主动报错 / 收尾的时间窗口
    private Duration emitterBuffer = Duration.ofSeconds(60);
}
