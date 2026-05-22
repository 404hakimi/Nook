package com.nook.framework.web;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 流式接口参数 (yaml 前缀 nook.web.streaming); ResponseBodyEmitter 比 install/op 端多留的余量, 留给 service 主动报错 / 收尾的时间窗口. */
@Data
@Component
@ConfigurationProperties(prefix = "nook.web.streaming")
public class WebStreamingProperties {

    private Duration emitterBuffer = Duration.ofSeconds(60);
}
