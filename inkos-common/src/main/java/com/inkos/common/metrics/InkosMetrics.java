package com.inkos.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 业务指标入口。
 *
 * <p>为什么用 {@link ObjectProvider} 而不是直接注入 {@link MeterRegistry}：
 * 指标是「有则记、无则跳过」的横切关注点。某个模块没引入 Actuator、
 * 或者切片测试只装配了部分上下文时，不应该因为缺这个 Bean 而起不来。
 *
 * <p>指标命名约定 {@code inkos.<领域>.<动作>}，标签只放低基数维度
 * （类型、结果），绝不放 userId / articleId —— 那会让时间序列基数爆炸。
 */
@Component
@RequiredArgsConstructor
public class InkosMetrics {

    /** 文章详情浏览 */
    public static final String ARTICLE_VIEW = "inkos.article.view";

    /**
     * 评论创建。
     *
     * <p>刻意<b>不</b>叫 {@code inkos.comment.created}：Prometheus 的 {@code <name>_created}
     * 是为 summary/histogram 保留的伴生指标后缀，Micrometer 会把结尾的 {@code created} 丢掉，
     * 结果暴露成 {@code inkos_comment_total} —— 名字与定义对不上，排查时极易误判。
     */
    public static final String COMMENT_SUBMITTED = "inkos.comment.submit";

    /** 互动切换（点赞 / 收藏） */
    public static final String REACTION_TOGGLED = "inkos.reaction.toggled";

    /** 被限流拒绝的请求 */
    public static final String RATE_LIMIT_REJECTED = "inkos.ratelimit.rejected";

    private final ObjectProvider<MeterRegistry> registryProvider;

    /**
     * 计数 +1。
     *
     * @param name 指标名，建议使用本类的常量
     * @param tags 交替出现的键值对，如 {@code "type", "like"}
     */
    public void count(String name, String... tags) {
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        registry.counter(name, tags).increment();
    }
}
