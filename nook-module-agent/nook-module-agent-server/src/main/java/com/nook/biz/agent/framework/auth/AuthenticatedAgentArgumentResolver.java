package com.nook.biz.agent.framework.auth;

import com.nook.biz.agent.service.AgentAuthService;
import com.nook.biz.node.api.resource.dto.ResourceServerRespDTO;
import jakarta.annotation.Resource;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Agent 鉴权参数解析器
 *
 * @author nook
 */
@Component
public class AuthenticatedAgentArgumentResolver implements HandlerMethodArgumentResolver {

    /** agent 明文上报的 serverId; token 不过线, 后台据此找 token 解密 auth 证明. */
    private static final String SERVER_HEADER = "X-Agent-Server";
    /** agent 用 token 加密的鉴权证明 (含时间戳); 解密成功即鉴权. */
    private static final String AUTH_HEADER = "X-Agent-Auth";

    @Resource
    private AgentAuthService agentAuthService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(AuthenticatedAgent.class)) return false;
        Class<?> type = parameter.getParameterType();
        return String.class.equals(type) || ResourceServerRespDTO.class.isAssignableFrom(type);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        ResourceServerRespDTO srv = agentAuthService.verifyAndGetServer(
                webRequest.getHeader(SERVER_HEADER), webRequest.getHeader(AUTH_HEADER));
        Class<?> type = parameter.getParameterType();
        if (String.class.equals(type)) return srv.getId();
        return srv;
    }
}
