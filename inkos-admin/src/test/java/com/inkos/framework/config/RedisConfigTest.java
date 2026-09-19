package com.inkos.framework.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Redis value 序列化器的单测。
 *
 * <p>刻意不启动 Spring 上下文、不连 Redis：要验证的是序列化配置本身，
 * 而它是最容易静默出错的一环 —— 类型信息一旦没写进去，反序列化会安静地退化成
 * {@code LinkedHashMap}，调用方拿到 ClassCastException 时早已远离现场。
 *
 * <p>这几条断言同时也是「本序列化器支持什么、不支持什么」的契约，
 * 尤其是 {@link #immutableJdkCollectionsLoseConcreteType()} 记录的那条边界。
 */
class RedisConfigTest {

    private final GenericJacksonJsonRedisSerializer serializer =
            new RedisConfig().redisValueSerializer();

    @Test
    @DisplayName("record 往返后仍是原类型，不是 LinkedHashMap")
    void roundTripPreservesRecordType() {
        Sample original = new Sample(42L, "砚知", LocalDateTime.of(2026, 9, 19, 16, 30, 0));

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored).isInstanceOf(Sample.class);
        assertThat((Sample) restored).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    @DisplayName("ArrayList 往返后容器与元素类型都保留")
    void roundTripPreservesMutableList() {
        ArrayList<Sample> original = new ArrayList<>(List.of(
                new Sample(1L, "甲", LocalDateTime.of(2026, 1, 1, 0, 0, 0)),
                new Sample(2L, "乙", LocalDateTime.of(2026, 2, 2, 0, 0, 0))));

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored).isInstanceOf(ArrayList.class);
        List<?> list = (List<?>) restored;
        assertThat(list).hasSize(2);
        assertThat(list.get(0)).isInstanceOf(Sample.class);
        assertThat((Sample) list.get(0)).usingRecursiveComparison().isEqualTo(original.get(0));
    }

    @Test
    @DisplayName("写入的是 JSON 文本，redis-cli 里直接可读（不是 JDK 二进制）")
    void storesReadableJson() {
        byte[] bytes = serializer.serialize(new Sample(7L, "砚知", LocalDateTime.of(2026, 9, 19, 16, 30, 0)));

        String json = new String(bytes);
        assertThat(json).contains("砚知").contains("Sample").doesNotContain("rO0AB");
    }

    /**
     * 已知限制：JDK 不可变集合（{@code List.of} / {@code Map.of}）<b>不能作为顶层值</b>。
     *
     * <p>原因是它们的实现类（{@code ImmutableCollections$List12} 等）是 final 且不是 record，
     * 不在 {@code NON_FINAL_AND_RECORDS} 的作用范围内 → 写入时不会带类型信息；
     * 而读取时按「根类型应当带类型 id」解析，于是把第一个元素当成类型名，直接失败。
     * <b>写进去的数据读不回来</b>，比类型退化更严重，所以用一条断言把它钉住。
     *
     * <p>这不是 Jackson 的缺陷，是默认类型信息作用范围决定的。绕开方式二选一：
     * 用可变集合 {@code new ArrayList<>(...)}，或用一个 POJO 包一层再存。
     */
    @Test
    @DisplayName("限制：顶层 List.of / Map.of 写入后无法读回（应改用 ArrayList 或 POJO 包装）")
    void topLevelImmutableCollectionsCannotBeRead() {
        byte[] immutableList = serializer.serialize(List.of("a", "b"));
        assertThatThrownBy(() -> serializer.deserialize(immutableList))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("type id");

        byte[] immutableMap = serializer.serialize(Map.of("k", "v"));
        assertThatThrownBy(() -> serializer.deserialize(immutableMap))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("绕开方式有效：可变集合与 POJO 包装都能正常往返")
    void workaroundsRoundTrip() {
        Object asMutable = serializer.deserialize(serializer.serialize(new ArrayList<>(List.of("a", "b"))));
        assertThat(asMutable).isInstanceOf(ArrayList.class).isEqualTo(List.of("a", "b"));

        Map<String, String> wrapped = new LinkedHashMap<>(Map.of("k", "v"));
        Object asMap = serializer.deserialize(serializer.serialize(wrapped));
        assertThat(asMap).isInstanceOf(Map.class).isEqualTo(wrapped);
    }

    /** 测试载体：字段覆盖 Long / String / LocalDateTime 三种常见类型 */
    record Sample(Long id, String name, LocalDateTime createdAt) {
    }
}
