package com.nook.biz.agent.framework.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标 Controller 方法参数: 由 AuthenticatedAgentArgumentResolver 读 X-Agent-Token Header,
 * 调 AgentAuthService.verifyAndGetServer 验完按参数类型注入 (String → serverId, ResourceServerDO → 完整 DO).
 * 校验失败抛 UNAUTHORIZED.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthenticatedAgent {
}
