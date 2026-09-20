package com.inkos.framework.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RedisLoadProtectionTest {
    @Test
    @SuppressWarnings("unchecked")
    void concurrentMissesLoadOnceAndLaterReadsHitCache() throws Exception {
        var values = new ConcurrentHashMap<String, Object>();
        var cache = new RedisCacheService(mock(RedisTemplate.class)) {
            @Override public Object get(String key) { return values.get(key); }
            @Override public void put(String key, Object value, Duration ttl) { values.put(key, value); }
        };
        var loads = new AtomicInteger();
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(12)) {
            var futures = new ArrayList<Future<String>>();
            for (int i = 0; i < 12; i++) {
                futures.add(executor.submit(() -> {
                    gate.await();
                    return cache.getOrLoad("same-article", Duration.ofMinutes(1), () -> {
                        loads.incrementAndGet();
                        try { Thread.sleep(30); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        return "article";
                    });
                }));
            }
            gate.countDown();
            for (var future : futures) assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("article");
        }
        assertThat(loads.get()).isEqualTo(1);
        assertThat(cache.getOrLoad("same-article", Duration.ofMinutes(1), () -> "wrong")).isEqualTo("article");
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisFailureFallsBackAndCooldownAvoidsRepeatedNetworkAttempts() {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(ops.get("article")).thenThrow(new IllegalStateException("offline"));
        var cache = new RedisCacheService(template);
        assertThat(cache.getOrLoad("article", Duration.ofMinutes(1), () -> "database")).isEqualTo("database");
        assertThat(cache.getOrLoad("article", Duration.ofMinutes(1), () -> "fresh database")).isEqualTo("fresh database");
        verify(ops, times(1)).get("article");
        assertThat(cache.isAvailable()).isFalse();
        assertThatThrownBy(() -> cache.getOrLoad("missing", Duration.ofMinutes(1), () -> { throw new IllegalArgumentException("not found"); }))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
