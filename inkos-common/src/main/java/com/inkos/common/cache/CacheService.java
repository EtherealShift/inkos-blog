package com.inkos.common.cache;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 缓存抽象。
 *
 * <h2>为什么抽象在 common、实现在 framework</h2>
 * {@code content} / {@code system} 需要缓存，但它们<b>不能</b>依赖 {@code framework} ——
 * 依赖方向只允许 {@code admin → framework → system → common} 与 {@code admin → content → common}。
 * 把接口放在 {@code common}，业务模块只依赖抽象；具体后端（当前是 Redis）由 {@code framework}
 * 提供，运行期注入。没有实现时降级为 {@link NoOpCacheService}，业务代码无需到处判空。
 *
 * <h2>唯一的硬性约定：任何方法都不许把缓存故障抛给调用方</h2>
 * 缓存是旁路。Redis 抖动、超时、序列化异常都必须被实现内部转成「未命中」并自行降级，
 * 否则一次缓存故障会直接变成接口 5xx。<b>唯一允许向外抛的异常来自 {@code loader} 本身</b>
 * （那是业务异常，例如「文章不存在」）。
 */
public interface CacheService {

    /**
     * 读取缓存值。
     *
     * @return 命中返回值；未命中、类型不符或缓存不可用时返回 {@code null}
     */
    Object get(String key);

    /**
     * 写入缓存值。
     *
     * @param ttl 存活时间，为 {@code null} 或值本身为 {@code null} 时不写入
     */
    void put(String key, Object value, Duration ttl);

    /**
     * 精确删除一个 key。
     *
     * <p>刻意只提供精确删除：前缀/模式删除在生产环境只能靠 {@code KEYS}（会阻塞 Redis）
     * 或 {@code SCAN}（大 key 空间下代价不可控）。需要「整组失效」时改用版本号，
     * 见 {@code CacheConstants.ARTICLE_LIST_VERSION_KEY}。
     *
     * @return 是否真的删掉了 key
     */
    boolean evict(String key);

    /**
     * 去重窗口内是否为第一次出现。
     *
     * <p>语义是「占坑」：返回 {@code true} 表示本次成功占了坑，调用方可以执行一次性动作。
     *
     * <p><b>刻意 fail-open</b>：缓存不可用时返回 {@code true}。用在阅读计数上时，
     * 宁可多记一次浏览，也不要因为 Redis 故障把所有浏览都丢掉。
     *
     * @param window 去重窗口
     */
    boolean firstSeen(String key, Duration window);

    /**
     * 读取计数器当前值，key 不存在或缓存不可用时返回 {@code 0}。
     */
    long counter(String key);

    /**
     * 计数器自增 1 并返回新值；缓存不可用时返回 {@code 0}。
     */
    long increment(String key);

    /**
     * 缓存后端当前是否可用。
     *
     * <p>实现应反映「最近一次操作是否失败」而不是每次都去 PING 一次后端 ——
     * 探测本身也是开销。因此这是一个<b>带滞后的判断</b>，用于决定「要不要白试一次」，
     * 不适合用作健康检查。
     */
    boolean isAvailable();

    /**
     * Cache-Aside：命中直接返回，未命中执行 {@code loader} 并回填。
     *
     * <p>两个刻意的选择：
     * <ul>
     *   <li><b>{@code loader} 返回 {@code null} 时不写缓存</b>。缓存空值会把「此刻没有」
     *       放大成「一段时间内都没有」，而本项目的序列化器也无法表达「带类型的 null」。</li>
     *   <li>默认实现不合并并发回源；Redis 实现用有界条带锁合并同实例内的并发 miss，
     *       不需要分布式锁。多实例仍可能各回源一次。</li>
     * </ul>
     *
     * <p>读回值的具体类型由序列化器写入的类型信息保证（{@code RedisConfig}），
     * 契约由 {@code RedisConfigTest} 覆盖。因此这里不额外做类型校验。
     */
    default <T> T getOrLoad(String key, Duration ttl, Supplier<T> loader) {
        Object cached = get(key);
        if (cached != null) {
            @SuppressWarnings("unchecked")
            T typed = (T) cached;
            return typed;
        }
        T loaded = loader.get();
        if (loaded != null) {
            put(key, loaded, ttl);
        }
        return loaded;
    }
}
