package com.inkos.common.core.constant;

/**
 * 缓存 key 前缀。统一集中管理，避免散落各处导致失效逻辑对不上。
 */
public final class CacheConstants {

    private CacheConstants() {
    }

    /** 登录用户信息 */
    public static final String LOGIN_USER_KEY = "inkos:login:user:";

    /** 文章详情 */
    public static final String ARTICLE_DETAIL_KEY = "inkos:article:detail:";

    /** 文章列表 */
    public static final String ARTICLE_LIST_KEY = "inkos:article:list:";

    /** 分类树 */
    public static final String CATEGORY_TREE_KEY = "inkos:category:tree";

    /** 标签云 */
    public static final String TAG_CLOUD_KEY = "inkos:tag:cloud";

    /** 阅读数去重 */
    public static final String VIEW_DEDUP_KEY = "inkos:view:dedup:";
}
