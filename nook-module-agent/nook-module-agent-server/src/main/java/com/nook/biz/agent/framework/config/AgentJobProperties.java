package com.nook.biz.agent.framework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * agent 模块定时任务配置 (映射 yml nook.agent.job.*)
 *
 * <p>用 @Component 注册以获得稳定 bean 名 agentJobProperties, 供 @Scheduled 的
 * SpEL "#{@agentJobProperties.xxx}" 引用 cron.
 *
 * @author nook
 */
@Data
@Component
@ConfigurationProperties(prefix = "nook.agent.job")
public class AgentJobProperties {

    /** Agent 心跳监控 / 告警 cron. */
    private String heartbeatTimeoutCron;
}
