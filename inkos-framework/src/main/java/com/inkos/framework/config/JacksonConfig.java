package com.inkos.framework.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局序列化配置。
 *
 * <p>Spring Boot 4 已从 Jackson 2 迁到 Jackson 3（groupId {@code tools.jackson}）：
 * <ul>
 *   <li>定制入口由 {@code Jackson2ObjectMapperBuilderCustomizer} 变为
 *       {@link JsonMapperBuilderCustomizer}，拿到的是不可变的 {@code JsonMapper.Builder}；</li>
 *   <li>Java 8 时间类型支持已并入 databind（{@code tools.jackson.databind.ext.javatime}），
 *       不再需要单独的 jsr310 依赖；</li>
 *   <li>注解包仍在 {@code com.fasterxml.jackson.annotation}，未随实现包迁移。</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    @Bean
    public JsonMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        DateTimeFormatter date = DateTimeFormatter.ofPattern(DATE_PATTERN);

        // 用一个模块集中声明覆盖项：模块在 Boot 默认配置之后注册，因此这里的序列化器优先。
        SimpleModule module = new SimpleModule("inkos-jackson");
        module.addSerializer(new LocalDateTimeSerializer(dateTime));
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTime));
        module.addSerializer(new LocalDateSerializer(date));
        module.addDeserializer(LocalDate.class, new LocalDateDeserializer(date));
        // Long 转字符串：JS 的 Number 只有 53 位精度，雪花 ID 直接下发会被截断
        module.addSerializer(Long.class, ToStringSerializer.instance);

        return builder -> builder.addModule(module);
    }
}
