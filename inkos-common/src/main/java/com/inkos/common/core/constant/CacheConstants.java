package com.inkos.common.core.constant;

import java.time.Duration;

/**
 * 缓存 key 前缀与存活时间。统一集中管理，避免散落各处导致失效逻辑对不上。
 *
 * <h2>两条必须遵守的规则</h2>
 * <ol>
 *   <li><b>不做前缀/模式删除。</b>失效只有两种手段：精确 key 删除，或版本号整体作废。
 *       不使用 {@code KEYS}（会阻塞 Redis 单线程）也不使用 {@code SCAN}
 *       （大 key 空间下代价不可控，且结果需要分批处理）。</li>
 *   <li><b>版本号 key 不设过期时间。</b>版本号一旦过期就会从 0 重新开始，
 *       与它同时代的老条目可能被「复活」。因此 {@code ARTICLE_LIST_VERSION_KEY}
 *       由 {@code RedisCacheService.increment} 写入，走的是不带 TTL 的原生 INCR。</li>
 * </ol>
 */
public final class CacheConstants {

    private CacheConstants() {
    }

    // ==================== 登录态 ====================

    /** 登录用户信息 */
    public static final String LOGIN_USER_KEY = "inkos:login:user:";

    // ==================== 内容 ====================

    /** 文章详情，后接 slug */
    public static final String ARTICLE_DETAIL_KEY = "inkos:article:detail:";

    /** 相关文章，后接「列表版本号:文章 id:条数」 */
    public static final String ARTICLE_RELATED_KEY = "inkos:article:related:";

    /** 文章列表，后接「列表版本号:查询条件指纹」 */
    public static final String ARTICLE_LIST_KEY = "inkos:article:list:";

    /**
     * 文章列表版本号。
     *
     * <p>这是「按命名空间整体失效」的实现方式：任何文章写操作只要把它 {@code +1}，
     * 全部列表缓存与相关文章缓存立刻全部作废，而<b>无需知道具体缓存过哪些 key</b>。
     * 老 key 不会被删除，会随各自 TTL 自然消失 —— 代价是短暂的内存冗余，
     * 换来的是 O(1) 的失效与不被阻塞的 Redis。
     */
    public static final String ARTICLE_LIST_VERSION_KEY = "inkos:article:list:version";

    /** 分类树 */
    public static final String CATEGORY_TREE_KEY = "inkos:category:tree";

    /** 标签云 */
    public static final String TAG_CLOUD_KEY = "inkos:tag:cloud";

    /** 首页语句列表，后接「版本号:条数」 */
    public static final String QUOTE_LIST_KEY = "inkos:quote:list:";

    /**
     * 首页语句列表版本号。语句的 key 里带条数（{@code limit} 由调用方决定，取值不可枚举），
     * 所以同样用版本号整体作废，而不是逐个删除。
     */
    public static final String QUOTE_LIST_VERSION_KEY = "inkos:quote:list:version";

    /** 阅读数去重，后接「文章 id:访客标识」 */
    public static final String VIEW_DEDUP_KEY = "inkos:view:dedup:";

    // ==================== 存活时间 ====================

    /**
     * 文章详情。
     *
     * <p>刻意取短值，因为详情里带着 {@code view_count} / {@code like_count} /
     * {@code comment_count} —— 这些计数在缓存期间不会刷新，
     * <b>TTL 就是「计数最多滞后多久」</b>。5 分钟是「读者察觉不到」与「数据库压力」的折中。
     */
    public static final Duration ARTICLE_DETAIL_TTL = Duration.ofMinutes(5);

    /** 文章列表与相关文章：列表对计数更不敏感，与详情取同一量级 */
    public static final Duration ARTICLE_LIST_TTL = Duration.ofMinutes(5);

    /** 字典类数据（分类树、标签云、首页语句）：读多写少、结构稳定，可以长一些 */
    public static final Duration DICTIONARY_TTL = Duration.ofMinutes(30);

    /** 阅读数去重窗口：同一访客 6 小时内重复打开同一篇文章只计一次 */
    public static final Duration VIEW_DEDUP_TTL = Duration.ofHours(6);
}
