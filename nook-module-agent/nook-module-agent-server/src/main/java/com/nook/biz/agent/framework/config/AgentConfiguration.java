package com.nook.biz.agent.framework.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 模块配置入口
 *
 * @author nook
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfiguration {
}
