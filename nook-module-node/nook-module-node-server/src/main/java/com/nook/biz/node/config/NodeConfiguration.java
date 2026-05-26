package com.nook.biz.node.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * nook-module-node 配置开关
 *
 * @author nook
 */
@Configuration
@EnableConfigurationProperties({
        ServerOpsProperties.class,
})
public class NodeConfiguration {
}
