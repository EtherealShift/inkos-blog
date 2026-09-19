package com.inkos.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置。
 *
 * <p>只引入 spring-security-crypto 这个纯工具包，不引入 Spring Security 的过滤器链 ——
 * 鉴权交给 Sa-Token，这里只要 BCrypt 算法。
 */
@Configuration
public class PasswordConfig {

    /**
     * BCrypt 强度。10 约 50~100ms，是安全性与登录延迟的常用平衡点；
     * 每 +1 计算量翻倍，调到 12 会让登录明显变慢且不利于压测。
     */
    private static final int STRENGTH = 10;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(STRENGTH);
    }
}
