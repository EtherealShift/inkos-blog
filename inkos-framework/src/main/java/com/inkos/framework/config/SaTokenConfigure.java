package com.inkos.framework.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 路由鉴权配置。
 *
 * <p>策略：默认全部需要登录，只放行显式声明的白名单。
 * 「默认拒绝」比「默认放行」安全 —— 新增接口时忘记加注解不会导致越权。
 *
 * <p>方法级鉴权（{@code @SaCheckPermission}）由同一个 {@link SaInterceptor} 处理。
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    /** 无需登录即可访问的路径 */
    private static final String[] WHITE_LIST = {
            "/api/v1/auth/login",
            "/api/v1/public/**",
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/doc.html",
            "/error"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> SaRouter
                        .match("/api/**")
                        .notMatch(WHITE_LIST)
                        .check(r -> StpUtil.checkLogin())))
                .addPathPatterns("/**")
                .order(0);
    }
}
