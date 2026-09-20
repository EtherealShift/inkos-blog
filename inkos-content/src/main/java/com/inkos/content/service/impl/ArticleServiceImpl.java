package com.inkos.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.inkos.common.core.constant.CacheConstants;
import com.inkos.common.core.domain.PageResult;
import com.inkos.common.core.enums.ArticleStatus;
import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.common.metrics.InkosMetrics;
import com.inkos.common.util.StrUtils;
import com.inkos.common.util.TextUtils;
import com.inkos.content.cache.ContentCache;
import com.inkos.content.dto.ArticleForm;
import com.inkos.content.dto.ArticleQuery;
import com.inkos.content.entity.Article;
import com.inkos.content.entity.ArticleTag;
import com.inkos.content.entity.Category;
import com.inkos.content.entity.Tag;
import com.inkos.content.mapper.ArticleMapper;
import com.inkos.content.mapper.ArticleTagMapper;
import com.inkos.content.mapper.CategoryMapper;
import com.inkos.content.mapper.TagMapper;
import com.inkos.content.port.AuthorNameResolver;
import com.inkos.content.port.CurrentUserProvider;
import com.inkos.content.service.ArticleService;
import com.inkos.content.vo.ArticleListVO;
import com.inkos.content.vo.ArticleVO;
import com.inkos.content.vo.SearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章服务实现。
 */
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    /**
     * 作者兜底值：取不到当前登录用户时使用。
     *
     * <p>只应出现在真实请求之外 —— 种子数据初始化、定时任务、消息消费等没有登录态的地方。
     * 正常请求一律走 {@link CurrentUserProvider}，不再无条件把文章挂在 1 号用户名下。
     */
    public static final Long DEFAULT_AUTHOR_ID = 1L;

    /** 批量查询 IN 子句的分片大小，避免超出数据库参数上限 */
    private static final int BATCH_SIZE = 1000;

    /** 摘要兜底截断长度 */
    private static final int SUMMARY_LENGTH = 200;

    /** 相关文章默认条数上限 */
    private static final int RELATED_LIMIT = 5;

    /** 相关文章条数硬上限：limit 来自外部入参，必须收敛，否则一次请求就能拉走整张表 */
    private static final int MAX_RELATED_LIMIT = 20;

    /**
     * 排序字段白名单：外部传入的 orderBy 只能命中这里，
     * 未命中一律退回默认排序。
     *
     * <p>映射到 {@link SFunction} 而不是列名字符串：列名一旦拼进 SQL 就再也拿不到
     * 编译期检查（改字段名 → 静默失效直到线上报错），而 lambda 由 MyBatis-Plus
     * 反解成真实列名，改名会直接编译失败。
     */
    private static final Map<String, SFunction<Article, ?>> ORDER_WHITELIST = Map.of(
            "publishedat", Article::getPublishedAt,
            "viewcount", Article::getViewCount,
            "updatetime", Article::getUpdateTime
    );

    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;

    /**
     * 作者名称解析端口。
     *
     * <p>用 {@link ObjectProvider} 而非直接注入：inkos-content 刻意不依赖 inkos-system，
     * 由上层（inkos-admin）提供适配器实现。没有实现时作者名降级为 null，
     * 而不是让整个内容模块启动失败。
     */
    private final ObjectProvider<AuthorNameResolver> authorNameResolverProvider;

    /**
     * 当前用户端口，同样用 {@link ObjectProvider}：没有适配器（content 模块单独运行）
     * 或不在请求线程里（种子数据初始化）时拿不到，回落到 {@link #DEFAULT_AUTHOR_ID}，
     * 而不是让文章创建直接失败。
     */
    private final ObjectProvider<CurrentUserProvider> currentUserProvider;

    /** 内容域缓存策略。没有缓存后端时内部自动退化为空实现，这里无需判空 */
    private final ContentCache contentCache;

    private final InkosMetrics metrics;

    // ==================== 查询 ====================

    @Override
    public PageResult<ArticleListVO> pagePublic(ArticleQuery query) {
        // 前台强制只看已发布，忽略外部传入的 status，避免越权读到草稿
        int status = ArticleStatus.PUBLISHED.getCode();
        if (StrUtils.isNotBlank(query.getKeyword())) {
            // 检索不进缓存：关键字的取值空间无界，缓存它等于把 Redis 的写权限交给调用方
            return pageByQuery(query, status, true);
        }
        return contentCache.getOrLoad(contentCache.articleListKey(query), CacheConstants.ARTICLE_LIST_TTL,
                () -> pageByQuery(query, status, true));
    }

    @Override
    public PageResult<ArticleListVO> pageAdmin(ArticleQuery query) {
        CurrentUserProvider user = currentUserProvider.getIfAvailable();
        if (user != null && user.currentUserIdOrNull() != null && !user.canManageAllArticles()) query.setAuthorId(user.currentUserIdOrNull());
        return pageByQuery(query, query.getStatus(), false);
    }

    /**
     * 分页查询公共实现。
     *
     * @param query         查询条件
     * @param statusOverride 非空时强制使用该状态，覆盖入参
     */
    private PageResult<ArticleListVO> pageByQuery(ArticleQuery query, Integer statusOverride, boolean publicOnly) {
        Page<Article> page = new Page<>(query.safePageNum(), query.safePageSize());
        baseMapper.selectPage(page, buildWrapper(query, statusOverride).eq(publicOnly, Article::getVisibility, 0));
        List<ArticleListVO> records = toListVos(page.getRecords());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<SearchResultVO> search(ArticleQuery query) {
        // 检索与列表的唯一语义差别就是「正文也匹配」，在这里强制打开，
        // 调用方无需（也不应）自己设置
        query.setSearchInContent(Boolean.TRUE);
        Page<Article> page = new Page<>(query.safePageNum(), query.safePageSize());
        baseMapper.selectPage(page, buildWrapper(query, ArticleStatus.PUBLISHED.getCode()).eq(Article::getVisibility, 0));

        List<ArticleListVO> vos = toListVos(page.getRecords());
        // 按 id 关联而不是按下标：toListVos 万一过滤了记录，下标就会错位
        String keyword = StrUtils.trimToEmpty(query.getKeyword());
        Map<Long, String> snippets = page.getRecords().stream()
                .collect(Collectors.toMap(Article::getId, article -> buildSnippet(article, keyword), (a, b) -> a));
        List<SearchResultVO> records = vos.stream()
                .map(vo -> new SearchResultVO(vo, vo.id() == null ? null : snippets.get(vo.id())))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<ArticleListVO> listPublishedByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Article> articles = list(new LambdaQueryWrapper<Article>()
                .in(Article::getId, ids)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .eq(Article::getVisibility, 0));
        if (articles.isEmpty()) {
            return List.of();
        }
        Map<Long, ArticleListVO> byId = toListVos(articles).stream()
                .collect(Collectors.toMap(ArticleListVO::id, Function.identity(), (a, b) -> a));
        // 顺序由调用方决定（例如收藏时间倒序），SQL 不负责排序；
        // 已下线或已删除的文章会在这里被自然跳过
        return ids.stream().map(byId::get).filter(Objects::nonNull).distinct().toList();
    }

    @Override
    public ArticleVO getBySlug(String slug) {
        return getBySlug(slug, null);
    }

    @Override
    public ArticleVO getBySlug(String slug, String viewerKey) {
        if (StrUtils.isBlank(slug)) {
            throw BusinessException.of(ResultCode.ARTICLE_NOT_FOUND);
        }
        // 详情走缓存：一次详情渲染原本要 5~6 次查询（正文 + 分类 + 标签 + 作者 + 浏览自增）
        ArticleVO vo = contentCache.getOrLoad(contentCache.articleDetailKey(slug),
                CacheConstants.ARTICLE_DETAIL_TTL, () -> toVo(requirePublishedBySlug(slug)));

        // 计数放在缓存之外：命中缓存时同样要计浏览量，只是按访客去重
        recordView(vo.id(), viewerKey);
        return vo;
    }

    /** 按 slug 取已发布文章，取不到抛业务异常。详情缓存的 loader 就是它 */
    private Article requirePublishedBySlug(String slug) {
        Article article = getOne(new LambdaQueryWrapper<Article>()
                .eq(Article::getSlug, slug)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .eq(Article::getVisibility, 0)
                .last("LIMIT 1"));
        if (article == null) {
            throw BusinessException.of(ResultCode.ARTICLE_NOT_FOUND);
        }
        return article;
    }

    /**
     * 记录一次浏览。
     *
     * <p>去重是刻意的。详情走缓存之后，计数器如果还是「每个请求 +1」，
     * 就变成「读压力降到数据库之外，写压力却原样打在数据库上」——
     * 一次 F5 就是一次 UPDATE。同一访客在窗口内重复打开只计一次。
     */
    private void recordView(Long articleId, String viewerKey) {
        if (!contentCache.shouldCountView(articleId, viewerKey)) {
            return;
        }
        baseMapper.incrementViewCount(articleId);
        metrics.count(InkosMetrics.ARTICLE_VIEW);
    }

    @Override
    public ArticleVO getByIdForEdit(Long id) {
        Article article = getArticleOrThrow(id);
        checkOwner(article);
        return toVo(article);
    }

    @Override
    public List<ArticleListVO> listRelated(Long articleId, int limit) {
        int size = limit <= 0 ? RELATED_LIMIT : Math.min(limit, MAX_RELATED_LIMIT);
        return contentCache.getOrLoad(contentCache.relatedArticleKey(articleId, size),
                CacheConstants.ARTICLE_LIST_TTL, () -> loadRelated(articleId, size));
    }

    @Override
    public int warmDetailCache(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        List<Article> articles = listByIds(articleIds).stream()
                .filter(article -> ArticleStatus.PUBLISHED == ArticleStatus.of(article.getStatus()) && Integer.valueOf(0).equals(article.getVisibility()))
                .toList();
        if (articles.isEmpty()) {
            return 0;
        }

        List<ArticleVO> vos = toDetailVos(articles);
        for (ArticleVO vo : vos) {
            contentCache.put(contentCache.articleDetailKey(vo.slug()), vo, CacheConstants.ARTICLE_DETAIL_TTL);
        }
        return vos.size();
    }

    private List<ArticleListVO> loadRelated(Long articleId, int size) {
        Article current = getArticleOrThrow(articleId);
        if (!Integer.valueOf(2).equals(current.getStatus()) || !Integer.valueOf(0).equals(current.getVisibility())) throw BusinessException.of(ResultCode.ARTICLE_NOT_FOUND);
        if (current.getCategoryId() == null) {
            return Collections.emptyList();
        }

        // 同分类下除自己以外的已发布文章。
        // 用 searchCount = false 的分页代替 last("LIMIT " + size)：既不会多一次 count 查询，
        // 也不必把数字拼进 SQL。id 兜底排序保证同一发布时间下顺序稳定。
        Page<Article> page = new Page<>(1, size, false);
        baseMapper.selectPage(page, new LambdaQueryWrapper<Article>()
                .eq(Article::getCategoryId, current.getCategoryId())
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .eq(Article::getVisibility, 0)
                .ne(Article::getId, current.getId())
                .orderByDesc(Article::getPublishedAt)
                .orderByDesc(Article::getId));
        return toListVos(page.getRecords());
    }

    // ==================== 写入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ArticleForm form) {
        Article article = new Article();
        article.setAuthorId(resolveAuthorId());
        article.setTitle(StrUtils.trim(form.title()));
        article.setSlug(resolveUniqueSlug(form.slug(), form.title(), null));
        article.setCategoryId(resolveCategoryId(form.categoryId()));
        article.setCoverUrl(StrUtils.trim(form.coverUrl()));
        article.setContentMd(form.contentMd());
        // 渲染器尚未接入，先用 Markdown 原文占位，保证前端有内容可展示
        article.setContentHtml(form.contentMd());
        article.setSummary(resolveSummary(form.summary(), form.contentMd()));
        article.setStatus(ArticleStatus.DRAFT.getCode());
        article.setVisibility(form.visibility() == null ? 0 : form.visibility());
        article.setWordCount(TextUtils.wordCount(form.contentMd()));
        article.setReadingMinutes(TextUtils.readingMinutes(form.contentMd()));
        article.setViewCount(0L);
        article.setLikeCount(0);
        article.setCommentCount(0);
        save(article);

        // 草稿不进前台列表，因此不必作废列表缓存；但标签的 article_count 是冗余列、
        // 已经被改动，标签云必须失效
        if (replaceArticleTags(article.getId(), Collections.emptyList(), resolveTagIds(form.tagIds()))) {
            contentCache.evictTagCloud();
        }
        return article.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ArticleForm form) {
        Article existing = getArticleOrThrow(form.id());
        checkOwner(existing);
        if (!isEditable(existing.getStatus())) {
            throw BusinessException.of(ResultCode.ARTICLE_NOT_EDITABLE);
        }

        Article update = new Article();
        update.setId(existing.getId());
        update.setTitle(StrUtils.trim(form.title()));
        String newSlug = resolveUniqueSlug(form.slug(), form.title(), existing.getId());
        update.setSlug(newSlug);
        update.setCategoryId(resolveCategoryId(form.categoryId()));
        update.setCoverUrl(StrUtils.trim(form.coverUrl()));
        update.setContentMd(form.contentMd());
        update.setContentHtml(form.contentMd());
        update.setSummary(resolveSummary(form.summary(), form.contentMd()));
        update.setVisibility(form.visibility() == null ? existing.getVisibility() : form.visibility());
        update.setWordCount(TextUtils.wordCount(form.contentMd()));
        update.setReadingMinutes(TextUtils.readingMinutes(form.contentMd()));
        update(update, new LambdaUpdateWrapper<Article>().eq(Article::getId, existing.getId()).set(Article::getCategoryId, update.getCategoryId()));

        // 关联表是物理表，先算差集再增删，避免全量重写的无谓写入
        List<Long> oldTagIds = selectTagIds(List.of(existing.getId()));
        List<Long> newTagIds = resolveTagIds(form.tagIds());
        boolean tagsChanged = replaceArticleTags(existing.getId(), oldTagIds, newTagIds);

        // 标题 / 摘要 / 正文 / 分类都会改变列表内容
        contentCache.invalidateArticleLists();
        contentCache.evictCategoryTree(); contentCache.evictTagCloud();
        // 改 slug 会让旧 URL 的详情缓存永远拿不到新数据，新旧两个 key 都要删
        contentCache.evictArticleDetail(existing.getSlug());
        contentCache.evictArticleDetail(newSlug);
        if (tagsChanged) {
            contentCache.evictTagCloud();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        Article existing = getArticleOrThrow(id);
        checkOwner(existing);

        // 草稿、待审、已下线都能发布，只有回收站例外
        if (ArticleStatus.RECYCLED == ArticleStatus.of(existing.getStatus())) {
            throw BusinessException.of(ResultCode.ARTICLE_NOT_EDITABLE);
        }

        // 传「实体 + 条件」而不是只给 UpdateWrapper：审计字段自动填充
        // （update_time / update_by）只在有实体时触发，纯 wrapper 会让更新时间停在旧值
        LocalDateTime now = LocalDateTime.now();
        Article update = new Article();
        update.setStatus(ArticleStatus.PUBLISHED.getCode());
        update.setPublishedAt(now);
        update(update, new LambdaUpdateWrapper<Article>().eq(Article::getId, id));

        contentCache.invalidateArticleLists();
        contentCache.evictCategoryTree(); contentCache.evictTagCloud();
        // 曾上线过的文章详情缓存里可能留着旧状态，且发布时间变了
        contentCache.evictArticleDetail(existing.getSlug());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long id) {
        Article existing = getArticleOrThrow(id);
        checkOwner(existing);

        Article update = new Article();
        if (!Integer.valueOf(2).equals(existing.getStatus())) throw BusinessException.of(ResultCode.CONFLICT, "只有已发布文章可以下线");
        update.setStatus(ArticleStatus.OFFLINE.getCode());
        update(update, new LambdaUpdateWrapper<Article>().eq(Article::getId, id));

        contentCache.invalidateArticleLists();
        contentCache.evictCategoryTree(); contentCache.evictTagCloud();
        contentCache.evictArticleDetail(existing.getSlug());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Article existing = getArticleOrThrow(id);
        checkOwner(existing);
        // @TableLogic 会把物理删除改写成 deleted = 1
        removeById(id);

        contentCache.invalidateArticleLists();
        contentCache.evictCategoryTree(); contentCache.evictTagCloud();
        contentCache.evictArticleDetail(existing.getSlug());
    }

    // ==================== 内部工具 ====================

    /** 命中片段在关键字两侧各保留的字符数 */
    private static final int SNIPPET_PADDING = 40;

    /**
     * 在正文里定位关键字并截取上下文。
     *
     * <p>命中只落在标题或摘要时返回 null —— 不硬凑一段与关键字无关的正文片段，
     * 那只会让用户以为搜错了。
     */
    private String buildSnippet(Article article, String keyword) {
        String content = article.getContentMd();
        if (StrUtils.isBlank(keyword) || StrUtils.isBlank(content)) {
            return null;
        }
        int index = StrUtils.indexOfIgnoreCase(content, keyword);
        if (index < 0) {
            return null;
        }
        int start = Math.max(0, index - SNIPPET_PADDING);
        int end = Math.min(content.length(), index + keyword.length() + SNIPPET_PADDING);
        // 正文里的换行与多余空白在片段里压平，否则响应里会出现大段空白
        String fragment = content.substring(start, end).replaceAll("\\s+", " ").trim();
        return (start > 0 ? "…" : "") + fragment + (end < content.length() ? "…" : "");
    }

    /** 组装分页条件，排序字段走白名单 */
    private LambdaQueryWrapper<Article> buildWrapper(ArticleQuery query, Integer status) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        if (query.getCategoryId() != null) {
            wrapper.eq(Article::getCategoryId, query.getCategoryId());
        }
        if (query.getAuthorId() != null) {
            wrapper.eq(Article::getAuthorId, query.getAuthorId());
        }
        if (status != null) {
            wrapper.eq(Article::getStatus, status);
        }
        if (query.getTagId() != null) {
            // 用 EXISTS 子查询而非联表，避免 join 造成的结果重复
            wrapper.exists("SELECT 1 FROM cms_article_tag at "
                    + "WHERE at.article_id = cms_article.id AND at.tag_id = {0}", query.getTagId());
        }
        if (StrUtils.isNotBlank(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            if (Boolean.TRUE.equals(query.getSearchInContent())) {
                // 检索接口：正文一并匹配。LIKE '%kw%' 无法走索引，
                // 内容量上来后应换成 MySQL ngram 全文索引或外接 Elasticsearch。
                wrapper.and(w -> w.like(Article::getTitle, keyword)
                        .or().like(Article::getSummary, keyword)
                        .or().like(Article::getContentMd, keyword));
            } else {
                wrapper.and(w -> w.like(Article::getTitle, keyword).or().like(Article::getSummary, keyword));
            }
        }
        applyOrder(wrapper, query.getOrderBy(), Boolean.TRUE.equals(query.getAsc()));
        return wrapper;
    }

    /**
     * 排序：白名单映射到 lambda 列，未命中或为空时按发布时间倒序。
     *
     * <p>两个细节：
     * <ul>
     *   <li>一律再追加 {@code id} 作为最后一级排序。只按 {@code published_at} 排序时，
     *       同一时间发布的多篇文章之间的相对顺序由数据库自行决定，
     *       <b>翻页会出现重复或漏行</b>；补一个唯一列才让分页稳定。</li>
     *   <li>不再用 {@code last("ORDER BY " + column)}：那是把列名拼成 SQL 字符串，
     *       绕过了 MyBatis-Plus 的列名解析，改字段名不会有任何编译期提示。</li>
     * </ul>
     */
    private void applyOrder(LambdaQueryWrapper<Article> wrapper, String orderBy, boolean asc) {
        SFunction<Article, ?> column = StrUtils.isBlank(orderBy)
                ? null
                : ORDER_WHITELIST.get(orderBy.trim().toLowerCase());
        if (column == null) {
            wrapper.orderByDesc(Article::getPublishedAt).orderByDesc(Article::getId);
            return;
        }
        wrapper.orderBy(true, asc, column).orderByDesc(Article::getId);
    }

    private void checkOwner(Article article) {
        CurrentUserProvider user = currentUserProvider.getIfAvailable();
        if (user != null && user.currentUserIdOrNull() != null && !user.canManageAllArticles()
                && !Objects.equals(user.currentUserIdOrNull(), article.getAuthorId()))
            throw BusinessException.of(ResultCode.FORBIDDEN, "只能管理自己的文章");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recycle(Long id) { changeRecycled(id, false); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id) { changeRecycled(id, true); }

    private void changeRecycled(Long id, boolean restore) {
        Article article = getArticleOrThrow(id);
        checkOwner(article);
        if (restore && !Integer.valueOf(4).equals(article.getStatus())) throw BusinessException.of(ResultCode.CONFLICT, "只有回收站文章可以恢复");
        Article update = new Article(); update.setId(id); update.setStatus(restore ? 0 : 4);
        updateById(update);
        contentCache.invalidateArticleLists();
        contentCache.evictCategoryTree(); contentCache.evictTagCloud(); contentCache.evictArticleDetail(article.getSlug());
    }

    /** 取文章，不存在直接抛业务异常 */
    private Article getArticleOrThrow(Long id) {
        if (id == null) {
            throw BusinessException.of(ResultCode.ARTICLE_NOT_FOUND);
        }
        Article article = getById(id);
        if (article == null) {
            throw BusinessException.of(ResultCode.ARTICLE_NOT_FOUND);
        }
        return article;
    }

    /**
     * 解析文章作者：优先当前登录用户，取不到才兜底。
     *
     * <p>端口缺失（content 单独运行）或不在请求线程（种子数据）时都会走到兜底分支。
     */
    private Long resolveAuthorId() {
        CurrentUserProvider provider = currentUserProvider.getIfAvailable();
        Long userId = provider == null ? null : provider.currentUserIdOrNull();
        return userId == null ? DEFAULT_AUTHOR_ID : userId;
    }

    /** 是否允许编辑：仅回收站不可编辑 */
    private boolean isEditable(Integer status) {
        return ArticleStatus.RECYCLED != ArticleStatus.of(status);
    }

    /**
     * slug 归一化并保证唯一：留空由标题生成，冲突时追加数字后缀。
     *
     * @param slug     表单传入值
     * @param title    标题，用于生成兜底 slug
     * @param excludeId 排除自身 id（修改场景）
     */
    private String resolveUniqueSlug(String slug, String title, Long excludeId) {
        String base = StrUtils.isBlank(slug) ? StrUtils.slugify(title) : StrUtils.slugify(slug);
        String candidate = base;
        int suffix = 2;
        while (slugExists(candidate, excludeId)) {
            String tail = "-" + suffix++;
            // 表列为 VARCHAR(220)，追加后缀前先预留空间
            String head = base.length() + tail.length() > 220
                    ? base.substring(0, 220 - tail.length())
                    : base;
            candidate = head + tail;
        }
        return candidate;
    }

    private boolean slugExists(String slug, Long excludeId) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getSlug, slug);
        if (excludeId != null) {
            wrapper.ne(Article::getId, excludeId);
        }
        return baseMapper.selectCount(wrapper) > 0;
    }

    /** 摘要兜底：为空时从正文提取纯文本 */
    private String resolveSummary(String summary, String contentMd) {
        if (StrUtils.isNotBlank(summary)) {
            return StrUtils.trim(summary);
        }
        return TextUtils.excerpt(contentMd, SUMMARY_LENGTH);
    }

    /** 校验分类存在，避免脏 category_id */
    private Long resolveCategoryId(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        if (categoryMapper.selectById(categoryId) == null) {
            throw BusinessException.of("分类不存在：" + categoryId);
        }
        return categoryId;
    }

    /** 校验标签 id 全部存在 */
    private List<Long> resolveTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> distinct = tagIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            return Collections.emptyList();
        }
        List<Tag> tags = tagMapper.selectByIds(distinct);
        if (tags.size() != distinct.size()) {
            Set<Long> found = tags.stream().map(Tag::getId).collect(Collectors.toSet());
            List<Long> missing = distinct.stream().filter(id -> !found.contains(id)).toList();
            throw BusinessException.of("标签不存在：" + missing);
        }
        return distinct;
    }

    /**
     * 按差集同步文章标签关联，并维护标签的文章计数。
     *
     * @return 关联是否真的发生了变化。调用方据此决定要不要作废标签云缓存 ——
     *         没有变化时多失效一次只是让缓存白建，但会让「编辑文章」这类高频操作
     *         每次都把 30 分钟的字典缓存清掉
     */
    private boolean replaceArticleTags(Long articleId, List<Long> oldTagIds, List<Long> newTagIds) {
        Set<Long> oldSet = new LinkedHashSet<>(oldTagIds == null ? List.of() : oldTagIds);
        Set<Long> newSet = new LinkedHashSet<>(newTagIds == null ? List.of() : newTagIds);

        List<Long> toRemove = oldSet.stream().filter(id -> !newSet.contains(id)).toList();
        List<Long> toAdd = newSet.stream().filter(id -> !oldSet.contains(id)).toList();
        if (toRemove.isEmpty() && toAdd.isEmpty()) {
            return false;
        }

        if (!toRemove.isEmpty()) {
            articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>()
                    .eq(ArticleTag::getArticleId, articleId)
                    .in(ArticleTag::getTagId, toRemove));
        }
        if (!toAdd.isEmpty()) {
            for (Long tagId : toAdd) {
                ArticleTag relation = new ArticleTag();
                relation.setArticleId(articleId);
                relation.setTagId(tagId);
                articleTagMapper.insert(relation);
            }
        }
        // 计数是冗余统计，允许并发下轻微偏差，故不做行锁
        toRemove.forEach(tagId -> adjustTagArticleCount(tagId, -1));
        toAdd.forEach(tagId -> adjustTagArticleCount(tagId, 1));
        return true;
    }

    /**
     * 在 SQL 侧增减标签文章数，避免读改写竞态。
     *
     * <p>增量用 {@code {0}} 占位符绑定成 PreparedStatement 参数，而不是拼进 SQL 文本：
     * 参数化后同一条语句可以被复用（MySQL 侧少一次解析），也免去了「这里拼的是 int、
     * 但下次有人改成 String」的隐患。
     */
    private void adjustTagArticleCount(Long tagId, int delta) {
        tagMapper.update(null, new LambdaUpdateWrapper<Tag>()
                .setSql(delta > 0
                        ? "article_count = article_count + {0}"
                        : "article_count = GREATEST(article_count - {0}, 0)", Math.abs(delta))
                .eq(Tag::getId, tagId));
    }

    /** 批量取文章 id 对应的标签 id */
    private List<Long> selectTagIds(List<Long> articleIds) {
        return selectArticleTagMap(articleIds).values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
    }

    /** 批量查关联表并按文章分组，分片规避 IN 参数上限 */
    private Map<Long, List<Long>> selectArticleTagMap(List<Long> articleIds) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (articleIds == null || articleIds.isEmpty()) {
            return result;
        }
        for (int from = 0; from < articleIds.size(); from += BATCH_SIZE) {
            List<Long> chunk = articleIds.subList(from, Math.min(from + BATCH_SIZE, articleIds.size()));
            List<ArticleTag> relations = articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTag>()
                    .in(ArticleTag::getArticleId, chunk));
            for (ArticleTag relation : relations) {
                result.computeIfAbsent(relation.getArticleId(), k -> new ArrayList<>()).add(relation.getTagId());
            }
        }
        return result;
    }

    /**
     * 批量解析作者昵称。
     *
     * <p>未装配 {@link AuthorNameResolver} 适配器时返回空 Map，作者名降级为 null，
     * 而不是抛异常 —— 内容模块可以独立运行。
     */
    private Map<Long, String> resolveAuthorNames(List<Article> articles) {
        Set<Long> authorIds = articles.stream()
                .map(Article::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (authorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        AuthorNameResolver resolver = authorNameResolverProvider.getIfAvailable();
        return resolver == null ? Collections.emptyMap() : resolver.resolveNames(authorIds);
    }

    /** 批量转换为列表 VO，集中查分类与标签，避免 N+1 */
    private List<ArticleListVO> toListVos(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<Long>> tagIdMap = selectArticleTagMap(articles.stream().map(Article::getId).toList());
        Set<Long> tagIds = tagIdMap.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        Map<Long, Tag> tagMap = tagIds.isEmpty()
                ? Collections.emptyMap()
                : tagMapper.selectByIds(tagIds).stream()
                        .collect(Collectors.toMap(Tag::getId, Function.identity(), (a, b) -> a));

        Set<Long> categoryIds = articles.stream()
                .map(Article::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> categoryNameMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : categoryMapper.selectByIds(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        Map<Long, String> authorNameMap = resolveAuthorNames(articles);

        List<ArticleListVO> vos = new ArrayList<>(articles.size());
        for (Article article : articles) {
            List<String> tags = tagIdMap.getOrDefault(article.getId(), List.of()).stream()
                    .map(tagMap::get)
                    .filter(Objects::nonNull)
                    .map(Tag::getName)
                    .toList();
            vos.add(new ArticleListVO(
                    article.getId(),
                    article.getTitle(),
                    article.getSlug(),
                    article.getSummary(),
                    article.getCoverUrl(),
                    article.getCategoryId(),
                    article.getCategoryId() == null ? null : categoryNameMap.get(article.getCategoryId()),
                    article.getAuthorId(),
                    authorNameMap.get(article.getAuthorId()),
                    tags,
                    article.getViewCount(),
                    article.getLikeCount(),
                    article.getCommentCount(),
                    article.getReadingMinutes(),
                    article.getPublishedAt(),
                    article.getStatus(),
                    article.getUpdateTime(), article.getVisibility()));
        }
        return vos;
    }

    /**
     * 批量转详情 VO。
     *
     * <p>预热要把 N 篇文章的详情一次性写进缓存。逐篇调 {@link #toVo} 会让分类 / 标签 /
     * 作者各查 N 次（N 篇 × 4 次查询）；这里先走一遍已经批量化了的 {@link #toListVos}，
     * 再在内存里补齐详情字段，总查询数从 4N 降到 4。
     */
    private List<ArticleVO> toDetailVos(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return Collections.emptyList();
        }
        // toListVos 按入参顺序遍历，因此下标可以安全对齐
        List<ArticleListVO> listVos = toListVos(articles);
        List<ArticleVO> detailVos = new ArrayList<>(articles.size());
        Map<Long, List<Long>> tagIds = selectArticleTagMap(articles.stream().map(Article::getId).toList());
        for (int index = 0; index < articles.size(); index++) {
            detailVos.add(toDetailVo(articles.get(index), listVos.get(index), tagIds.getOrDefault(articles.get(index).getId(), List.of())));
        }
        return detailVos;
    }

    /** 单篇转详情 VO，复用批量逻辑保证字段口径一致 */
    private ArticleVO toVo(Article article) {
        return toDetailVo(article, toListVos(List.of(article)).get(0), selectTagIds(List.of(article.getId())));
    }

    private ArticleVO toDetailVo(Article article, ArticleListVO list, List<Long> tagIds) {
        return new ArticleVO(
                list.id(),
                list.title(),
                list.slug(),
                list.summary(),
                list.coverUrl(),
                list.categoryId(),
                list.categoryName(),
                list.authorId(),
                list.authorName(),
                list.tags(),
                list.viewCount(),
                list.likeCount(),
                list.commentCount(),
                list.readingMinutes(),
                list.publishedAt(),
                article.getContentMd(),
                article.getContentHtml(),
                article.getStatus(),
                article.getVisibility(),
                article.getQualityScore(),
                article.getCreateTime(),
                article.getUpdateTime(), tagIds);
    }
}
