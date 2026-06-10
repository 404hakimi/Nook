package com.nook.framework.security.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import com.nook.framework.security.sign.PortalSignInterceptor;
import com.nook.framework.security.stp.StpMemberUtil;
import com.nook.framework.security.stp.StpSystemUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * sa-token 路由鉴权配置
 *
 * @author nook
 */
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    private final PortalSignInterceptor portalSignInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 客户端验签先于登录态校验; 订阅链接豁免 (第三方客户端/浏览器直接访问)
        registry.addInterceptor(portalSignInterceptor)
                .addPathPatterns("/portal/**")
                .excludePathPatterns("/portal/sub/**");

        registry.addInterceptor(new SaInterceptor(handler -> {
            // /admin/** 走后台体系，登录/登出 + agent binary 下载 (走 X-Agent-Token 鉴权) 放行
            SaRouter.match("/admin/**")
                    .notMatch("/admin/system/auth/login", "/admin/system/auth/logout",
                            "/admin/agent-dist/download-bin")
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
