package com.nook.biz.agent.framework.auth;

import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.agent.service.AgentAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 解析 @AuthenticatedAgent 参数: 读 X-Agent-Token Header → verifyAndGetServer.
 * 按参数类型分发: String → server.id (绝大多数 controller 只需要 id);
 * ResourceServerDO → 完整 DO (需要其它字段时用).
 */
@Component
@RequiredArgsConstructor
public class AuthenticatedAgentArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String TOKEN_HEADER = "X-Agent-Token";

    private final AgentAuthService agentAuthService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(AuthenticatedAgent.class)) return false;
        Class<?> type = parameter.getParameterType();
        return String.class.equals(type) || ResourceServerDO.class.isAssignableFrom(type);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        ResourceServerDO srv = agentAuthService.verifyAndGetServer(webRequest.getHeader(TOKEN_HEADER));
        Class<?> type = parameter.getParameterType();
        if (String.class.equals(type)) return srv.getId();
        return srv;
    }
}
