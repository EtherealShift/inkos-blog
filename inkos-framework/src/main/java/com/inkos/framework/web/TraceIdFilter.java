package com.inkos.framework.web;

import com.inkos.common.util.StrUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求追踪 ID。
 *
 * <p>做三件事：取（或生成）请求 ID、放进 MDC 让所有日志带上它、回写到响应头。
 * 排障时拿到一个 ID 就能把一次请求跨越的所有日志串起来。
 *
 * <p>两个容易忽略的点：
 * <ul>
 *   <li>外部传入的 ID 要做长度与格式约束 —— 它会被写进日志，不能原样信任；</li>
 *   <li>必须在 {@code finally} 里清 MDC：处理请求的线程是复用的
 *       （平台线程池、虚拟线程的载体线程），不清理会把上一个请求的 ID 串到下一个。</li>
 * </ul>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "traceId";

    private static final int MAX_TRACE_ID_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(HEADER);
        if (StrUtils.isBlank(traceId) || traceId.length() > MAX_TRACE_ID_LENGTH) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
