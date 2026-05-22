package com.nook.biz.node.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IP 池参数 (yaml 前缀 nook.node.resource.ip-pool)
 *
 * @author nook
 */
@Data
@ConfigurationProperties(prefix = "nook.node.resource.ip-pool")
public class ResourceIpPoolProperties {

    // SELECT-then-UPDATE 防双卖偶尔会与并发兑换抢同一行; 失败时重试本数
    private int occupyRetry = 2;
}
