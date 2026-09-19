package com.inkos.framework.ratelimit;

import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.common.metrics.InkosMetrics;
import com.inkos.common.util.StrUtils;
import com.inkos.framework.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流切面：滑动窗口日志实现。
 *
 * <p>为什么不用固定窗口计数：固定窗口在边界处会放过接近 2 倍配额
 * （00:59 打满一轮，01:00 立刻又能打满）。滑动窗口记录每次命中的时间戳，
 * 判定精确，代价是每个 key 需要保存最多 {@code count} 个时间戳 —— 可接受。
 *
 * <p>放在框架层而不是网关层，是因为它要按「用户」维度限流：
 * 到网关时登录态还没解析出来，只能按 IP，而 IP 后面可能是整栋楼的用户。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimiterAspect {

    /** 超过这个 key 数量就触发一次过期清理，避免长期运行内存只增不减 */
    private static final int EVICT_THRESHOLD = 10_000;

    private final InkosMetrics metrics;

    /** key -> 命中时间戳队列（毫秒），即滑动窗口日志 */
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = buildKey(joinPoint, rateLimit);
        long now = System.currentTimeMillis();
        long windowMillis = rateLimit.period() * 1000L;

        Deque<Long> hits = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        // 只锁同一个 key 的队列，不同 key 之间不互相阻塞
        synchronized (hits) {
            while (!hits.isEmpty() && now - hits.peekFirst() >= windowMillis) {
                hits.pollFirst();
            }
            if (hits.size() >= rateLimit.count()) {
                metrics.count(InkosMetrics.RATE_LIMIT_REJECTED, "key", key);
                log.warn("触发限流 key={} 限制={}/{}s", key, rateLimit.count(), rateLimit.period());
                throw BusinessException.of(ResultCode.TOO_MANY_REQUESTS, rateLimit.message());
            }
            hits.addLast(now);
        }

        evictIfNeeded(now, windowMillis);
        return joinPoint.proceed();
    }

    private String buildKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        String base = StrUtils.isNotBlank(rateLimit.key())
                ? rateLimit.key()
                : ((MethodSignature) joinPoint.getSignature()).getDeclaringType().getSimpleName()
                        + "." + joinPoint.getSignature().getName();

        String subject;
        if (rateLimit.dimension() == RateLimit.Dimension.IP) {
            subject = "ip" + SecurityUtils.getClientIp();
        } else {
            Long userId = SecurityUtils.getUserIdOrNull();
            if (userId != null) {
                subject = "u" + userId;
            } else if (rateLimit.dimension() == RateLimit.Dimension.USER) {
                // 强制用户维度但未登录：所有匿名请求共享一份配额，宁可收紧不可放开
                subject = "anonymous";
            } else {
                subject = "ip" + SecurityUtils.getClientIp();
            }
        }
        return base + ":" + subject;
    }

    /** 惰性清理：只在 key 数量超阈值时扫一遍，避免每条请求都全量遍历 */
    private void evictIfNeeded(long now, long windowMillis) {
        if (windows.size() <= EVICT_THRESHOLD) {
            return;
        }
        windows.entrySet().removeIf(entry -> {
            Deque<Long> hits = entry.getValue();
            synchronized (hits) {
                while (!hits.isEmpty() && now - hits.peekFirst() >= windowMillis) {
                    hits.pollFirst();
                }
                return hits.isEmpty();
            }
        });
    }
}
