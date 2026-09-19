package com.inkos.framework.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * Redis 序列化配置。
 *
 * <h2>为什么不用 Boot 自动配置的 RedisTemplate</h2>
 * 它默认用 JDK 序列化：存进去是二进制，{@code redis-cli} 里读不出内容，
 * 且实体类一改字段就反序列化失败。这里换成 JSON —— key 用字符串、value 用 JSON，
 * 可读、可跨版本演进、线上可直接排查。
 *
 * <h2>为什么必须开启「默认类型信息」</h2>
 * 不开的话，反序列化到 {@code Object} 只能得到 {@code LinkedHashMap}，类型静默丢失，
 * 调用方在很远处才拿到 ClassCastException。写入 {@code @class} 是代价，换来的是能还原对象。
 *
 * <h2>三处非默认的选择，每一处都有具体理由</h2>
 * <ol>
 *   <li><b>{@code As.WRAPPER_ARRAY} 而非 Spring Data 默认的 {@code As.PROPERTY}</b>：
 *       {@code As.PROPERTY} 靠往 JSON 对象里塞一个属性来记录类型，
 *       而 JSON <b>数组</b>无法附加属性 —— 顶层是 List 时类型信息写不进去，
 *       读取时反而按「应有类型 id」去解析而报错。用数组包裹则 POJO / List / Map 一视同仁。</li>
 *   <li><b>{@code NON_FINAL_AND_RECORDS} 而非 {@code NON_FINAL}</b>：
 *       本项目 DTO / VO 大量使用 record，而 <b>record 是 final 类</b> ——
 *       用 {@code NON_FINAL} 会跳过它们，等于白开类型信息。
 *       这是 Jackson 3 新增的档位（Jackson 3 已移除 {@code EVERYTHING}）。</li>
 *   <li><b>白名单 validator 而非 {@code enableUnsafeDefaultTyping()}</b>：
 *       后者允许反序列化任意类型，等于把 Jackson 历史上的反序列化 gadget 链重新打开。
 *       这里只放行 {@code com.inkos} 与 JDK 的容器 / 时间 / 数值类型。</li>
 * </ol>
 *
 * <h2>已知边界</h2>
 * JDK 不可变集合（{@code List.of} / {@code Map.of}）作为<b>顶层值</b>时，
 * 具体容器类型是 final 且非 record，因此不带类型信息，读回会退化成
 * {@code ArrayList} / {@code LinkedHashMap}（仍是合法的 List / Map，只是具体类型丢了）。
 * 需要精确还原时用 POJO 包一层，或改用 {@code new ArrayList<>(...)}。
 * 该边界由 {@code RedisConfigTest} 显式覆盖，避免日后被当成偶发问题排查。
 *
 * <p>另：这里没有复用 Spring 容器里的 {@code ObjectMapper}。它服务于 HTTP 响应，
 * 在上面开启类型信息会让接口返回的 JSON 多出类型字段。Redis 需要独立的 mapper，
 * {@link GenericJacksonJsonRedisSerializer} 的 builder 正是为此提供的。
 */
@Configuration
public class RedisConfig {

    /** 允许写入类型信息的包前缀白名单 */
    private static final String[] ALLOWED_TYPE_PREFIXES = {
            "com.inkos.",
            "java.util.",
            "java.time.",
            "java.math.",
    };

    @Bean
    public GenericJacksonJsonRedisSerializer redisValueSerializer() {
        PolymorphicTypeValidator validator = allowedTypeValidator();
        return GenericJacksonJsonRedisSerializer.builder()
                .customize(builder -> builder.activateDefaultTyping(
                        validator, DefaultTyping.NON_FINAL_AND_RECORDS, JsonTypeInfo.As.WRAPPER_ARRAY))
                .build();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       GenericJacksonJsonRedisSerializer redisValueSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(redisValueSerializer);
        template.setHashValueSerializer(redisValueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    private PolymorphicTypeValidator allowedTypeValidator() {
        BasicPolymorphicTypeValidator.Builder builder = BasicPolymorphicTypeValidator.builder();
        for (String prefix : ALLOWED_TYPE_PREFIXES) {
            builder.allowIfSubType(prefix);
        }
        return builder.build();
    }
}
