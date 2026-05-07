package com.nook.framework.security.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import com.nook.framework.security.stp.StpMemberUtil;
import com.nook.framework.security.stp.StpSystemUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * sa-token 路由鉴权：按 URL 前缀分发到 system / member 两个体系。
 * 鉴权失败抛 NotLoginException，由 SaTokenExceptionHandler 转 Result。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
            // /admin/** 走后台体系，登录/登出端点放行
            SaRouter.match("/admin/**")
                    .notMatch("/admin/system/auth/login", "/admin/system/auth/logout")
                    .check(r -> StpSystemUtil.checkLogin());

            // /portal/** 走会员体系；登录/注册/订阅链接(走 sub_token)放行
            SaRouter.match("/portal/**")
                    .notMatch(
                            "/portal/member/auth/login",
                            "/portal/member/auth/register",
                            "/portal/member/auth/logout",
                            "/portal/sub/**")
                    .check(r -> StpMemberUtil.checkLogin());
        })).addPathPatterns("/**");
    }
}
