package com.inkos.framework.config;

import com.inkos.common.util.StrUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：跨域。
 *
 * <p>生产环境务必通过 {@code inkos.cors.allowed-origins} 收敛到具体域名；
 * 配合 {@code allowCredentials=true} 时浏览器不允许使用 {@code *}，因此这里默认给的是本地开发地址。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${inkos.cors.allowed-origins:}")
    private String allowedOrigins;

    @Value("${inkos.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${inkos.cors.max-age:3600}")
    private long maxAge;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(StrUtils.split(allowedOrigins, ','))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition", "X-Request-Id")
                .allowCredentials(allowCredentials)
                .maxAge(maxAge);
    }
}
