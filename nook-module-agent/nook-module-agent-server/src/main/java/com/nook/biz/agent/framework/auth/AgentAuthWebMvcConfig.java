package com.nook.biz.agent.framework.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Agent 鉴权 WebMvc 配置
 *
 * @author nook
 */
@Configuration
@RequiredArgsConstructor
public class AgentAuthWebMvcConfig implements WebMvcConfigurer {

    private final AuthenticatedAgentArgumentResolver authenticatedAgentArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authenticatedAgentArgumentResolver);
    }
}
