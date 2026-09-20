package com.inkos.common.cache;

import java.time.Duration;

/**
 * 空缓存实现：所有读都「未命中」、所有写都丢弃。
 *
 * <p>存在的意义不是「假装有缓存」，而是让业务代码只写一条路径。
 * 没有它的话，每个用到缓存的地方都要写成
 * {@code cache == null ? load() : cache.getOrLoad(...)}，
 * 于是缓存就变成了一件「必须记得判空」的事 —— 那才是真正的故障来源。
 *
 * <p>行为上的三个约定：
 * <ul>
 *   <li>{@link #getOrLoad} 走父接口默认实现，{@code get} 恒返回 {@code null}，
 *       因此每次都执行 loader，等价于「没接缓存」；</li>
 *   <li>{@link #firstSeen} 返回 {@code true}（fail-open），阅读计数退化为每次都记；</li>
 *   <li>{@link #isAvailable()} 返回 {@code false}，调用方可据此跳过预热、避免无谓开销。</li>
 * </ul>
 */
public final class NoOpCacheService implements CacheService {

    /** 无状态，可直接共享一个实例 */
    public static final NoOpCacheService INSTANCE = new NoOpCacheService();

    private NoOpCacheService() {
    }

    @Override
    public Object get(String key) {
        return null;
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        // 无缓存后端，丢弃即可
    }

    @Override
    public boolean evict(String key) {
        return false;
    }

    @Override
    public boolean firstSeen(String key, Duration window) {
        return true;
    }

    @Override
    public long counter(String key) {
        return 0L;
    }

    @Override
    public long increment(String key) {
        return 0L;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
