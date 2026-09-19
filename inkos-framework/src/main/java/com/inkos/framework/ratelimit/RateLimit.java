package com.inkos.framework.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流。
 *
 * <p>用于挡住「一个账号/IP 高频打同一个接口」的场景：登录爆破、评论刷屏。
 * 实现是单机内存滑动窗口，<b>不依赖 Redis</b> —— 多实例部署时每个实例各限各的，
 * 精确限流需要换成集中式计数器（加 Redis 后把
 * {@link RateLimiterAspect} 的计数后端替换掉即可，注解不用动）。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 窗口内允许的最大请求数 */
    int count() default 10;

    /** 窗口长度（秒） */
    int period() default 60;

    /** 限流维度 */
    Dimension dimension() default Dimension.AUTO;

    /** 业务标识。留空时取「类名.方法名」，同一方法的多处限制会互相影响 */
    String key() default "";

    /** 触发限流时返回给调用方的提示 */
    String message() default "请求过于频繁，请稍后再试";

    enum Dimension {
        /** 已登录按用户维度，未登录按 IP —— 大多数接口的合理默认 */
        AUTO,
        /** 强制按用户；未登录时退化为全局共享配额 */
        USER,
        /** 强制按 IP */
        IP
    }
}
