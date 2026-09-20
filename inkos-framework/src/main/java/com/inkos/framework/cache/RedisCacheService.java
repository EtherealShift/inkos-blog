package com.inkos.framework.cache;

import com.inkos.common.cache.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * {@link CacheService} 的 Redis 实现。
 *
 * <h2>一、故障必须被吃掉，而且要「熔断」而不是每次都重试</h2>
 * Spring Data Redis 的连接超时是 3s（见 {@code application.yml}）。如果只是逐个操作
 * try/catch，Redis 挂掉时<b>每个请求都要先等满 3 秒</b>才降级到数据库 —— 缓存故障会
 * 被放大成全站变慢。所以这里用一个时间窗熔断：一旦某次操作失败，接下来
 * {@value #FAILURE_COOLDOWN_SECONDS} 秒内直接判定「不可用」，不再真的去连。
 * 代价是 Redis 恢复后最多有 {@value #FAILURE_COOLDOWN_SECONDS} 秒的空窗期，
 * 对缓存来说完全可以接受。
 *
 * <h2>二、文档型值走 JSON 序列化，计数器与去重标记走裸命令</h2>
 * 值序列化器（{@code RedisConfig}）开启了默认类型信息，写入的是
 * {@code ["java.util.ArrayList",[...]]} 这种「类型 id + 值」的包装数组。
 * 而 Redis 原生 {@code INCR} 写进去的是裸数字 {@code 5}，<b>裸数字没有类型 id，
 * 用值序列化器读会直接失败</b>（{@code RedisConfigTest} 已经把这个边界钉住了）。
 * 因此这里刻意分成两条路：
 * <ul>
 *   <li>{@link #get}/{@link #put}/{@link #evict} —— 存取对象，走值序列化器；</li>
 *   <li>{@link #counter}/{@link #increment}/{@link #firstSeen} —— 存取 Redis 原生标量，
 *       走 {@link RedisCallback} 裸命令，不经过序列化器。</li>
 * </ul>
 *
 * <h2>三、为什么落盘前要把集合复制成可变实现</h2>
 * 序列化器用 {@code NON_FINAL_AND_RECORDS} 决定是否写类型信息，而
 * {@code List.of()} / {@code Collections.emptyList()} / {@code Stream.toList()} 的
 * 实现类是 <b>final 且不是 record</b> → 不写类型信息 → 作为顶层值时<b>读不回来</b>。
 * 业务侧每次手动包一层 {@code ArrayList} 太容易漏，所以在 {@link #put} 里统一兜住：
 * 落盘前把 {@code List}/{@code Set}/{@code Map} 一律复制成可变实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheService implements CacheService {

    /** 去重标记的占位值：只关心 key 是否存在，内容无意义 */
    private static final String MARKER = "1";

    /** TTL 抖动比例：整批 key 同时过期会把压力集中到一个瞬间（缓存雪崩） */
    private static final double TTL_JITTER_RATIO = 0.1d;

    /** 失败后的熔断时长 */
    private static final long FAILURE_COOLDOWN_SECONDS = 30L;

    private static final long FAILURE_COOLDOWN_NANOS = Duration.ofSeconds(FAILURE_COOLDOWN_SECONDS).toNanos();

    private final RedisTemplate<String, Object> redisTemplate;

    // 有界条带锁：同一实例内合并同 key 的并发回源，不引入分布式锁或无界 key 映射。
    private final Object[] loadLocks = IntStream.range(0, 256).mapToObj(i -> new Object()).toArray();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String key, Duration ttl, Supplier<T> loader) {
        Object cached = get(key);
        if (cached != null) return (T) cached;
        if (!available()) return loader.get();
        synchronized (loadLocks[Math.floorMod(key.hashCode(), loadLocks.length)]) {
            cached = get(key);
            if (cached != null) return (T) cached;
            T loaded = loader.get();
            if (loaded != null) put(key, loaded, ttl);
            return loaded;
        }
    }

    /** 熔断截止时刻（{@link System#nanoTime} 坐标系）。初值为最小值，表示「可用」 */
    private final AtomicLong cooldownUntilNanos = new AtomicLong(Long.MIN_VALUE);

    @Override
    public Object get(String key) {
        if (!available()) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RuntimeException e) {
            markUnavailable("GET", key, e);
            return null;
        }
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        if (value == null || ttl == null || !available()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, mutableCopy(value), withJitter(ttl));
        } catch (RuntimeException e) {
            markUnavailable("SET", key, e);
        }
    }

    @Override
    public boolean evict(String key) {
        if (!available()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        } catch (RuntimeException e) {
            markUnavailable("DEL", key, e);
            return false;
        }
    }

    @Override
    public boolean firstSeen(String key, Duration window) {
        if (window == null || !available()) {
            return true;
        }
        try {
            // setIfAbsent 返回 null 表示「没拿到明确结果」，按 fail-open 处理：宁可多计一次
            return !Boolean.FALSE.equals(redisTemplate.opsForValue().setIfAbsent(key, MARKER, window));
        } catch (RuntimeException e) {
            markUnavailable("SETNX", key, e);
            return true;
        }
    }

    @Override
    public long counter(String key) {
        if (!available()) {
            return 0L;
        }
        try {
            // 裸 GET：值可能是其他方式（或 redis-cli）写进来的纯数字，绕过序列化器解析
            byte[] raw = redisTemplate.execute(
                    (RedisCallback<byte[]>) connection -> connection.get(bytes(key)));
            return parseLong(raw);
        } catch (RuntimeException e) {
            markUnavailable("GET", key, e);
            return 0L;
        }
    }

    @Override
    public long increment(String key) {
        if (!available()) {
            return 0L;
        }
        try {
            // 裸 INCR：原子自增，且刻意不带 TTL —— 版本号过期会让老缓存「复活」
            Long value = redisTemplate.execute(
                    (RedisCallback<Long>) connection -> connection.incr(bytes(key)));
            return value == null ? 0L : value;
        } catch (RuntimeException e) {
            markUnavailable("INCR", key, e);
            return 0L;
        }
    }

    @Override
    public boolean isAvailable() {
        return available();
    }

    // ==================== 内部实现 ====================

    /**
     * 用「比较」而不是「相减再判正负」判断熔断窗口。
     *
     * <p>{@link System#nanoTime} 的取值原点任意、<b>可以为负</b>，而初值是
     * {@link Long#MIN_VALUE}；相减会溢出，让应用刚启动就误判为「不可用」。
     */
    private boolean available() {
        return System.nanoTime() >= cooldownUntilNanos.get();
    }

    /**
     * 标记失败并开启熔断窗口。
     *
     * <p>只在「从可用转入不可用」时打一条 WARN：故障期间每个请求都会走到这里，
     * 每次都打日志会把日志文件刷爆，反而埋掉真正的线索。
     */
    private void markUnavailable(String operation, String key, RuntimeException cause) {
        boolean wasAvailable = available();
        cooldownUntilNanos.set(System.nanoTime() + FAILURE_COOLDOWN_NANOS);
        if (wasAvailable) {
            log.warn("Redis 缓存不可用，{} 秒内直连数据库；首次失败操作={} key={} 原因={}",
                    FAILURE_COOLDOWN_SECONDS, operation, key, cause.getMessage());
            log.debug("Redis 缓存故障详情", cause);
        }
    }

    /**
     * 落盘前把 JDK 不可变集合换成可变实现，绕开序列化器「顶层不可变集合读不回来」的边界。
     *
     * <p>只做一层浅拷贝：嵌套结构里的集合都有明确的声明类型，反序列化时按声明类型
     * 还原即可，不受这条边界影响。
     */
    private Object mutableCopy(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof Set<?> set) {
            return new LinkedHashSet<>(set);
        }
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>(map);
        }
        return value;
    }

    /**
     * 给 TTL 加 ±{@value #TTL_JITTER_RATIO} 的抖动。
     *
     * <p>同一批 key（例如预热写入的分类树、标签云、语句）如果 TTL 完全相同，
     * 就会在同一秒集体过期，然后在同一秒集体回源查库。抖动把它们打散。
     */
    private Duration withJitter(Duration ttl) {
        long millis = ttl.toMillis();
        if (millis <= 0) {
            return ttl;
        }
        long spread = (long) (millis * TTL_JITTER_RATIO);
        if (spread <= 0) {
            return ttl;
        }
        long jittered = millis + ThreadLocalRandom.current().nextLong(-spread, spread + 1);
        return Duration.ofMillis(Math.max(1L, jittered));
    }

    private static byte[] bytes(String key) {
        return key.getBytes(StandardCharsets.UTF_8);
    }

    private static long parseLong(byte[] raw) {
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(new String(raw, StandardCharsets.UTF_8).trim());
        } catch (NumberFormatException e) {
            // 计数器 key 里出现了非数字（例如被人手改过）：当作 0，不影响主流程
            return 0L;
        }
    }
}
