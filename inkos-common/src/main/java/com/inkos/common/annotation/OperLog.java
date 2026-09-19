package com.inkos.common.annotation;

import com.inkos.common.core.enums.LogBusinessType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。由 {@code OperLogAspect} 在框架层统一拦截并落库。
 *
 * <p>放在 common 层是为了让 admin（控制器）与 framework（切面）都能引用，而无需互相依赖。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /** 模块标题，如「文章管理」 */
    String title();

    /** 业务类型 */
    LogBusinessType businessType() default LogBusinessType.OTHER;

    /** 是否记录请求参数 */
    boolean saveRequestData() default true;

    /** 是否记录响应结果 */
    boolean saveResponseData() default false;
}
