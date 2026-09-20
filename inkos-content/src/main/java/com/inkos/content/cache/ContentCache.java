package com.inkos.content.cache;

import com.inkos.common.cache.CacheService;
import com.inkos.common.cache.NoOpCacheService;
import com.inkos.common.core.constant.CacheConstants;
import com.inkos.common.util.StrUtils;
import com.inkos.content.dto.ArticleQuery;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 内容域的缓存策略集中点。
 *
 * <h2>为什么要有这一层，而不是在各 Service 里直接拼 key</h2>
 * 缓存最容易出的问题不是「没缓存」，而是<b>失效逻辑与被缓存的数据对不上</b>：
 * 读的时候用了 A key，写的时候删了 B key；或者新增了一个读路径却没人记得失效它。
 * 把 key 的构造、TTL 的选择、失效的触发全部收在这一个类里，就能一眼看清
 * 「内容域到底缓存了哪些东西、什么操作会让它们失效」。
 *
 * <h2>失效只有三种形态</h2>
 * <ol>
 *   <li><b>精确删除</b>：分类树、标签云、文章详情 —— key 是确定的；</li>
 *   <li><b>版本号整体作废</b>：文章列表、相关文章、首页语句 ——
 *       key 里带 {@code limit} 或查询条件，取值不可枚举，只能整体换代号；</li>
 *   <li><b>什么都不做，交给 TTL</b>：只有计数值变了（浏览量、点赞数、评论数）时。
 *       计数变化非常频繁，为它作废整个列表缓存不划算；这类字段的 TTL 就是
 *       「最多滞后多久」的承诺。</li>
 * </ol>
 *
 * <h2>降级</h2>
 * 用 {@link ObjectProvider} 取缓存后端：{@code content} 模块刻意不依赖 {@code framework}
 * （缓存实现所在层），因此单独编译、单独跑测试时拿不到实现，此时自动退化为
 * {@link NoOpCacheService}，所有读写都变成「未命中 + 直连数据库」。
 * 这与 {@code AuthorNameResolver} 的处理方式一致。
 */
@Component
public class ContentCache {

    private final ObjectProvider<CacheService> cacheServiceProvider;

    public ContentCache(ObjectProvider<CacheService> cacheServiceProvider) {
        this.cacheServiceProvider = cacheServiceProvider;
    }

    /** 缓存后端是否可用，供预热流程判断「值不值得试」 */
    public boolean isAvailable() {
        return cache().isAvailable();
    }

    // ==================== 通用读写 ====================

    /** Cache-Aside 读：命中直接返回，未命中执行 loader 并回填（loader 返回 null 不写缓存） */
    public <T> T getOrLoad(String key, Duration ttl, Supplier<T> loader) {
        return cache().getOrLoad(key, ttl, loader);
    }

    /** 预热专用写入：已经拿到数据时直接用，省掉一次多余的读 */
    public void put(String key, Object value, Duration ttl) {
        cache().put(key, value, ttl);
    }

    // ==================== key 构造 ====================

    /** 文章详情 key（按 slug） */
    public String articleDetailKey(String slug) {
        return CacheConstants.ARTICLE_DETAIL_KEY + "workspace-v2:" + slug;
    }

    /**
     * 文章列表 key。
     *
     * <p>指纹只包含<b>会影响结果集的查询条件</b>：状态在前台被强制为「已发布」，
     * 关键字检索刻意不进缓存（关键字的取值空间无界，缓存它等于让任何人用随机关键字
     * 把 Redis 填满）。因此这两个字段都不在指纹里。
     */
    public String articleListKey(ArticleQuery query) {
        return CacheConstants.ARTICLE_LIST_KEY + "workspace-v2:" + articleListVersion() + ':' + fingerprint(query);
    }

    /** 相关文章 key：结果随文章集合变化，因此同样挂在列表版本号上 */
    public String relatedArticleKey(Long articleId, int limit) {
        return CacheConstants.ARTICLE_RELATED_KEY + "workspace-v2:" + articleListVersion() + ':' + articleId + ':' + limit;
    }

    /** 首页语句列表 key */
    public String quoteListKey(int limit) {
        return CacheConstants.QUOTE_LIST_KEY + "hero-v3:" + quoteListVersion() + ':' + limit;
    }

    // ==================== 失效 ====================

    /**
     * 文章集合发生结构性变化（新增发布 / 修改 / 下线 / 删除）时调用。
     *
     * <p>一次 {@code INCR} 就让全部文章列表与相关文章缓存作废，
     * 不需要知道曾经缓存过哪些条件组合。
     */
    public void invalidateArticleLists() {
        afterCommit(() -> cache().increment(CacheConstants.ARTICLE_LIST_VERSION_KEY));
    }

    /** 删除某篇文章的详情缓存。slug 为空（例如老数据）时静默跳过 */
    public void evictArticleDetail(String slug) {
        if (StrUtils.isNotBlank(slug)) {
            afterCommit(() -> cache().evict(articleDetailKey(slug)));
        }
    }

    /** 分类树失效：分类本身增删改，或分类下的文章数变化 */
    public void evictCategoryTree() {
        afterCommit(() -> cache().evict(CacheConstants.CATEGORY_TREE_KEY));
    }

    /** 标签云失效：标签增删改，或标签下的文章数变化 */
    public void evictTagCloud() {
        afterCommit(() -> cache().evict(CacheConstants.TAG_CLOUD_KEY));
    }

    /** 首页语句失效 */
    public void invalidateQuoteLists() {
        afterCommit(() -> cache().increment(CacheConstants.QUOTE_LIST_VERSION_KEY));
    }

    // ==================== 阅读计数 ====================

    /**
     * 本次访问是否应该计入浏览量。
     *
     * <p>识别不出访客（{@code viewerKey} 为空，例如后台或非 Web 调用）时返回 {@code true}，
     * 退化成「每次都计」，保持与引入缓存之前一致的行为。
     */
    public boolean shouldCountView(Long articleId, String viewerKey) {
        if (articleId == null) {
            return false;
        }
        if (StrUtils.isBlank(viewerKey)) {
            return true;
        }
        return cache().firstSeen(CacheConstants.VIEW_DEDUP_KEY + articleId + ':' + viewerKey,
                CacheConstants.VIEW_DEDUP_TTL);
    }

    // ==================== 内部 ====================

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }

    private CacheService cache() {
        return cacheServiceProvider.getIfAvailable(() -> NoOpCacheService.INSTANCE);
    }

    private long articleListVersion() {
        return cache().counter(CacheConstants.ARTICLE_LIST_VERSION_KEY);
    }

    private long quoteListVersion() {
        return cache().counter(CacheConstants.QUOTE_LIST_VERSION_KEY);
    }

    private String fingerprint(ArticleQuery query) {
        return String.join("|",
                String.valueOf(query.getCategoryId()),
                String.valueOf(query.getTagId()),
                String.valueOf(query.getAuthorId()),
                StrUtils.lowerCase(StrUtils.trimToEmpty(query.getOrderBy())),
                String.valueOf(Boolean.TRUE.equals(query.getAsc())),
                String.valueOf(query.safePageNum()),
                String.valueOf(query.safePageSize()));
    }
}
