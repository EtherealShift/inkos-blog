package com.inkos.admin.config;

import com.inkos.common.cache.CacheService;
import com.inkos.common.core.domain.PageQuery;
import com.inkos.content.dto.ArticleQuery;
import com.inkos.content.service.ArticleService;
import com.inkos.content.service.CategoryService;
import com.inkos.content.service.QuoteService;
import com.inkos.content.service.TagService;
import com.inkos.content.vo.ArticleListVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动缓存预热。
 *
 * <h2>为什么预热的是「读路径」而不是直接写缓存</h2>
 * 这里调用的全是正常业务方法（{@code tree()} / {@code cloud()} / {@code pagePublic()}），
 * 由它们自己的 Cache-Aside 逻辑把结果写进 Redis。好处是<b>预热写入的 key 与格式
 * 不可能和真实读取时不一致</b> —— 如果预热另写一套 key 或另拼一份 JSON，
 * 那就是一个只在「第一次访问」才暴露的隐蔽 bug 来源。
 *
 * <h2>为什么挂在 {@link ApplicationReadyEvent} 上</h2>
 * 它晚于所有 {@code ApplicationRunner}，因此 {@code DevDataInitializer} 的种子数据
 * 已经提交。挂在 {@code CommandLineRunner} 上就得靠 {@code @Order} 去猜顺序，
 * 而「谁先谁后」本来就是事件点本身要表达的东西。
 *
 * <h2>失败必须放行</h2>
 * 预热是加速手段，不是启动前置条件：Redis 连不上、数据有问题，博客都必须能起来。
 * 因此这里整体 catch，最坏结果只是「首次访问回源查库」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "inkos.cache.warm-up.enabled", havingValue = "true", matchIfMissing = true)
public class CacheWarmUpRunner {

    /** 预热前几页文章列表（以及它们的详情）。首页 + 一次「加载更多」 */
    private static final int WARM_PAGES = 2;

    /** 与前台默认取用的条数保持一致，避免预热了一个永远没人用的 limit */
    private static final int QUOTE_LIMIT = 8;

    private final CacheService cacheService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final QuoteService quoteService;
    private final ArticleService articleService;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!cacheService.isAvailable()) {
            log.warn("缓存后端不可用，跳过预热；公开接口将直接读数据库");
            return;
        }

        warm("分类", () -> categoryService.tree().size());
        warm("标签", () -> tagService.cloud().size());
        warm("首页语句", () -> quoteService.listPublic(QUOTE_LIMIT).size());
        warm("文章列表及详情", () -> articleService.warmDetailCache(warmArticleLists()));
    }

    private void warm(String name, java.util.function.IntSupplier action) {
        long startedAt = System.nanoTime();
        try { log.info("缓存预热 {}：{} 项，{} ms", name, action.getAsInt(), elapsedMillis(startedAt)); }
        catch (RuntimeException e) { log.warn("缓存预热 {} 失败，按需回源：{}", name, e.getMessage()); }
    }

    /**
     * 依次预热前几页文章列表，并收集文章 id 供详情预热使用。
     *
     * <p>调用 {@code pagePublic} 本身就完成了列表预热，因此这里不额外写缓存。
     */
    private List<Long> warmArticleLists() {
        List<Long> articleIds = new ArrayList<>();
        for (int pageNum = 1; pageNum <= WARM_PAGES; pageNum++) {
            ArticleQuery query = new ArticleQuery();
            query.setPageNum(pageNum);
            query.setPageSize(PageQuery.DEFAULT_PAGE_SIZE);
            articleService.pagePublic(query).getRecords().stream()
                    .map(ArticleListVO::id)
                    .forEach(articleIds::add);
        }
        return articleIds;
    }

    private static long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }
}
