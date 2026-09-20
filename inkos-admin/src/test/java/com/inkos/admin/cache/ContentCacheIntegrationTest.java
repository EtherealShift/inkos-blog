package com.inkos.admin.cache;

import com.inkos.common.core.constant.CacheConstants;
import com.inkos.common.core.domain.PageQuery;
import com.inkos.common.core.domain.PageResult;
import com.inkos.content.cache.ContentCache;
import com.inkos.content.dto.ArticleQuery;
import com.inkos.content.service.ArticleService;
import com.inkos.content.service.CategoryService;
import com.inkos.content.service.QuoteService;
import com.inkos.content.service.TagService;
import com.inkos.content.vo.ArticleListVO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缓存链路的集成测试：验证「预热真的写进了 Redis」「读回来的对象是原类型」
 * 「失效真的换了代号」。
 *
 * <p>与 {@code CachedValueRoundTripTest} 的分工：那个验证序列化契约（不连 Redis），
 * 这个验证真实的 Redis 读写链路。这里需要本机有 MySQL 与 Redis，因此在
 * {@link BeforeEach} 里用 {@link Assumptions} 探测：<b>拿不到就跳过而不是失败</b> ——
 * 缓存没起来不该让整个构建红掉，但也绝不能静默地把断言跳过而不留痕迹
 * （surefire 会把 skipped 计数报出来）。
 *
 * <p>用 {@code @Order} 固定执行顺序：验证「预热写入」的那个用例必须最先跑，
 * 否则前面已经触发过读路径，就无法区分 key 是预热写的还是读缓存时写的。
 */
@ActiveProfiles({"dev", "test"})
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContentCacheIntegrationTest {

    /** 与 CacheWarmUpRunner.QUOTE_LIMIT 保持一致 */
    private static final int WARM_QUOTE_LIMIT = 8;

    @Test
    @Order(7)
    @DisplayName("真实 Redis 命中不执行数据库 loader，缓存有有效 TTL")
    void redisHitSkipsDatabaseLoader() {
        String key = "inkos:test:loader:" + java.util.UUID.randomUUID();
        var loads = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Supplier<PageResult<ArticleListVO>> loader = () -> {
            loads.incrementAndGet();
            return articleService.pageAdmin(warmUpQuery());
        };
        try {
            PageResult<ArticleListVO> first = contentCache.getOrLoad(key, java.time.Duration.ofMinutes(1), loader);
            PageResult<ArticleListVO> second = contentCache.getOrLoad(key, java.time.Duration.ofMinutes(1), loader);
            assertThat(loads.get()).isEqualTo(1);
            assertThat(second.getRecords()).usingRecursiveComparison().isEqualTo(first.getRecords());
            assertThat(redisTemplate.getExpire(key)).isBetween(1L, 66L);
        } finally {
            redisTemplate.delete(key);
        }
    }

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ContentCache contentCache;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TagService tagService;

    @Autowired
    private QuoteService quoteService;

    @Autowired
    private ArticleService articleService;

    @BeforeEach
    void requireReachableRedis() {
        Assumptions.assumeTrue(redisReachable(), "Redis 不可用，跳过缓存集成测试");
    }

    @Test
    @Order(1)
    @DisplayName("启动预热：字典数据与首屏文章已经被写进 Redis（无需触发任何读路径）")
    @SuppressWarnings("unchecked")
    void warmUpWroteCachesOnStartup() {
        assertThat(redisTemplate.hasKey(CacheConstants.CATEGORY_TREE_KEY))
                .as("分类树应由 CacheWarmUpRunner 预热写入")
                .isTrue();
        assertThat(redisTemplate.hasKey(CacheConstants.TAG_CLOUD_KEY))
                .as("标签云应由 CacheWarmUpRunner 预热写入")
                .isTrue();
        assertThat(redisTemplate.hasKey(contentCache.quoteListKey(WARM_QUOTE_LIMIT)))
                .as("首页语句应由 CacheWarmUpRunner 预热写入")
                .isTrue();

        // 列表 key 里带版本号与条件指纹，用与预热完全相同的查询复现它。
        // 注意这一步只是「算 key + 读 key」，不会写缓存，否则断言就自我实现了。
        String listKey = contentCache.articleListKey(warmUpQuery());
        assertThat(redisTemplate.hasKey(listKey))
                .as("首屏文章列表应由 CacheWarmUpRunner 预热写入")
                .isTrue();

        // 直接从 Redis 取回列表，既证明 JSON 能被还原成 PageResult<ArticleListVO>，
        // 又能拿到 slug 去核对详情缓存
        Object cached = redisTemplate.opsForValue().get(listKey);
        assertThat(cached).isInstanceOf(PageResult.class);
        PageResult<ArticleListVO> page = (PageResult<ArticleListVO>) cached;
        assertThat(page.getRecords()).isNotEmpty();
        assertThat(page.getRecords().get(0)).isInstanceOf(ArticleListVO.class);

        String slug = page.getRecords().get(0).slug();
        assertThat(redisTemplate.hasKey(contentCache.articleDetailKey(slug)))
                .as("首屏文章的详情应由 CacheWarmUpRunner 预热写入")
                .isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("走一次真实读路径：结果从 Redis 还原后与数据库口径一致（分页元信息不能丢）")
    void cachedListKeepsPaginationMetadata() {
        ArticleQuery query = warmUpQuery();
        PageResult<ArticleListVO> first = articleService.pagePublic(query);
        // 第二次必然是缓存命中；若反序列化把 total/pages 丢掉，这里就会露出来
        PageResult<ArticleListVO> second = articleService.pagePublic(query);

        assertThat(second.getTotal()).isEqualTo(first.getTotal()).isPositive();
        assertThat(second.getPageNum()).isEqualTo(first.getPageNum());
        assertThat(second.getPageSize()).isEqualTo(first.getPageSize());
        assertThat(second.getPages()).isEqualTo(first.getPages());
        assertThat(second.getRecords()).usingRecursiveComparison().isEqualTo(first.getRecords());
    }

    @Test
    @Order(3)
    @DisplayName("列表失效靠版本号：一次自增就让全部列表 key 换代号，无需知道缓存过哪些 key")
    void invalidatingListsRotatesTheVersionKey() {
        ArticleQuery query = warmUpQuery();
        String before = contentCache.articleListKey(query);

        contentCache.invalidateArticleLists();

        String after = contentCache.articleListKey(query);
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    @Order(4)
    @DisplayName("文章详情：先写缓存再精确删除，删除后立刻回到未命中")
    void detailCacheIsWrittenThenEvicted() {
        String slug = firstPublishedSlug();
        // 计数走访客去重，这里用固定标识，避免同一窗口内反复累加种子数据的浏览量
        articleService.getBySlug(slug, "u:integration-test");

        String detailKey = contentCache.articleDetailKey(slug);
        assertThat(redisTemplate.hasKey(detailKey)).isTrue();

        contentCache.evictArticleDetail(slug);
        assertThat(redisTemplate.hasKey(detailKey)).isFalse();
    }

    @Test
    @Order(5)
    @DisplayName("阅读数去重：同一访客在窗口内只有第一次算数")
    void viewCountingIsDeduplicatedPerViewer() {
        // 独立标识避免上一次测试尚未过期的 Redis 去重键影响本次断言。
        String viewerKey = "u:dedup-probe:" + java.util.UUID.randomUUID();

        assertThat(contentCache.shouldCountView(1L, viewerKey)).isTrue();
        assertThat(contentCache.shouldCountView(1L, viewerKey)).isFalse();
        // 不同访客互不影响
        assertThat(contentCache.shouldCountView(1L, viewerKey + "-other")).isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("分类树：精确删除后下一次读取会重新回源并回填")
    void categoryTreeIsEvictedAndRebuilt() {
        categoryService.tree();
        assertThat(redisTemplate.hasKey(CacheConstants.CATEGORY_TREE_KEY)).isTrue();

        contentCache.evictCategoryTree();
        assertThat(redisTemplate.hasKey(CacheConstants.CATEGORY_TREE_KEY)).isFalse();

        assertThat(categoryService.tree()).isNotEmpty();
        assertThat(redisTemplate.hasKey(CacheConstants.CATEGORY_TREE_KEY)).isTrue();
    }

    // ==================== 工具 ====================

    /** 与 CacheWarmUpRunner 预热首屏时构造的查询保持一致 */
    private ArticleQuery warmUpQuery() {
        ArticleQuery query = new ArticleQuery();
        query.setPageNum(1);
        query.setPageSize(PageQuery.DEFAULT_PAGE_SIZE);
        return query;
    }

    private String firstPublishedSlug() {
        PageResult<ArticleListVO> page = articleService.pagePublic(warmUpQuery());
        assertThat(page.getRecords()).as("需要种子数据里的已发布文章").isNotEmpty();
        return page.getRecords().get(0).slug();
    }

    /** 主动 PING 探活：{@code isAvailable()} 只反映熔断状态，不代表此刻真的连得上 */
    private boolean redisReachable() {
        try {
            String pong = redisTemplate.execute((RedisCallback<String>) RedisConnection::ping);
            return "PONG".equalsIgnoreCase(pong);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
