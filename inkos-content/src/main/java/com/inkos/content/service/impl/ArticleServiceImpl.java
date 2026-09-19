package com.inkos.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.inkos.common.core.domain.PageResult;
import com.inkos.common.core.enums.ArticleStatus;
import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.common.metrics.InkosMetrics;
import com.inkos.common.util.StrUtils;
import com.inkos.common.util.TextUtils;
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

    /** 当前登录作者的临时兜底值。framework 模块接入 Sa-Token 后应从上下文获取。 */
    public static final Long DEFAULT_AUTHOR_ID = 1L;

    /** 批量查询 IN 子句的分片大小，避免超出数据库参数上限 */
    private static final int BATCH_SIZE = 1000;

    /** 摘要兜底截断长度 */
    private static final int SUMMARY_LENGTH = 200;

    /** 相关文章默认条数上限 */
    private static final int RELATED_LIMIT = 5;

    /**
     * 排序字段白名单：外部传入的 orderBy 只能命中这里，
     * 未命中一律退回默认排序，杜绝 SQL 注入。
     */
    private static final Map<String, String> ORDER_WHITELIST = Map.of(
            "publishedat", "published_at",
            "viewcount", "view_count",
            "updatetime", "update_time"
    );

    private static final String COLUMN_PUBLISHED_AT = "published_at";
    private static final String COLUMN_VIEW_COUNT = "view_count";
    private static final String COLUMN_UPDATE_TIME = "update_time";

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
    private final InkosMetrics metrics;

    // ==================== 查询 ====================

    @Override
    public PageResult<ArticleListVO> pagePublic(ArticleQuery query) {
        // 前台强制只看已发布，忽略外部传入的 status，避免越权读到草稿
        return pageByQuery(query, ArticleStatus.PUBLISHED.getCode());
    }

    @Override
    public PageResult<ArticleListVO> pageAdmin(ArticleQuery query) {
        return pageByQuery(query, query.getStatus());
    }

    /**
     * 分页查询公共实现。
     *
     * @param query         查询条件
     * @param statusOverride 非空时强制使用该状态，覆盖入参
     */
    private PageResult<ArticleListVO> pageByQuery(ArticleQuery query, Integer statusOverride) {
        Page<Article> page = new Page<>(query.safePageNum(), query.safePageSize());
        baseMapper.selectPage(page, buildWrapper(query, statusOverride));
        List<ArticleListVO> records = toListVos(page.getRecords());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<SearchResultVO> search(ArticleQuery query) {
        // 检索与列表的唯一语义差别就是「正文也匹配」，在这里强制打开，
        // 调用方无需（也不应）自己设置
        query.setSearchInContent(Boolean.TRUE);
        Page<Article> page = new Page<>(query.safePageNum(), query.safePageSize());
        baseMapper.selectPage(page, buildWrapper(query, ArticleStatus.PUBLISHED.getCode()));

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
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode()));
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
        if (StrUtils.isBlank(slug)) {
            throw BusinessException.of(ResultCode.ARTICLE_NOT_FOUND);
        }
        Article article = getOne(new LambdaQueryWrapper<Article>()
                .eq(Article::getSlug, slug)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .last("LIMIT 1"));
        if (article == null) {
            throw BusinessException.of(ResultCode.ARTICLE_NOT_FOUND);
        }

        // 先取详情再计数：即使自增失败也不应影响正文返回
        ArticleVO vo = toVo(article);
        baseMapper.incrementViewCount(article.getId());
        metrics.count(InkosMetrics.ARTICLE_VIEW);
        return vo;
    }

    @Override
    public ArticleVO getByIdForEdit(Long id) {
        return toVo(getArticleOrThrow(id));
    }

    @Override
    public List<ArticleListVO> listRelated(Long articleId, int limit) {
        Article current = getArticleOrThrow(articleId);
        int size = limit <= 0 ? RELATED_LIMIT : limit;
        if (current.getCategoryId() == null) {
            return Collections.emptyList();
        }

        // 同分类下除自己以外的已发布文章
        List<Article> articles = list(new LambdaQueryWrapper<Article>()
                .eq(Article::getCategoryId, current.getCategoryId())
                .eq(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .ne(Article::getId, current.getId())
                .orderByDesc(Article::getPublishedAt)
                .last("LIMIT " + size));
        return toListVos(articles);
    }

    // ==================== 写入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ArticleForm form) {
        Article article = new Article();
        article.setAuthorId(DEFAULT_AUTHOR_ID);
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

        replaceArticleTags(article.getId(), Collections.emptyList(), resolveTagIds(form.tagIds()));
        return article.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ArticleForm form) {
        Article existing = getArticleOrThrow(form.id());
        if (!isEditable(existing.getStatus())) {
            throw BusinessException.of(ResultCode.ARTICLE_NOT_EDITABLE);
        }

        Article update = new Article();
        update.setId(existing.getId());
        update.setTitle(StrUtils.trim(form.title()));
        update.setSlug(resolveUniqueSlug(form.slug(), form.title(), existing.getId()));
        update.setCategoryId(resolveCategoryId(form.categoryId()));
        update.setCoverUrl(StrUtils.trim(form.coverUrl()));
        update.setContentMd(form.contentMd());
        update.setContentHtml(form.contentMd());
        update.setSummary(resolveSummary(form.summary(), form.contentMd()));
        update.setVisibility(form.visibility() == null ? existing.getVisibility() : form.visibility());
        update.setWordCount(TextUtils.wordCount(form.contentMd()));
        update.setReadingMinutes(TextUtils.readingMinutes(form.contentMd()));
        updateById(update);

        // 关联表是物理表，先算差集再增删，避免全量重写的无谓写入
        List<Long> oldTagIds = selectTagIds(List.of(existing.getId()));
        List<Long> newTagIds = resolveTagIds(form.tagIds());
        replaceArticleTags(existing.getId(), oldTagIds, newTagIds);
    }

    @Override
    public void publish(Long id) {
        Article existing = getArticleOrThrow(id);

        // 草稿、待审、已下线都能发布，只有回收站例外
        if (ArticleStatus.RECYCLED == ArticleStatus.of(existing.getStatus())) {
            throw BusinessException.of(ResultCode.ARTICLE_NOT_EDITABLE);
        }
        update(new LambdaUpdateWrapper<Article>()
                .set(Article::getStatus, ArticleStatus.PUBLISHED.getCode())
                .set(Article::getPublishedAt, LocalDateTime.now())
                .eq(Article::getId, id));
    }

    @Override
    public void offline(Long id) {
        getArticleOrThrow(id);
        update(new LambdaUpdateWrapper<Article>()
                .set(Article::getStatus, ArticleStatus.OFFLINE.getCode())
                .eq(Article::getId, id));
    }

    @Override
    public void delete(Long id) {
        getArticleOrThrow(id);
        // @TableLogic 会把物理删除改写成 deleted = 1
        removeById(id);
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

    /** 排序白名单映射：未命中或为空时按发布时间倒序 */
    private void applyOrder(LambdaQueryWrapper<Article> wrapper, String orderBy, boolean asc) {
        if (StrUtils.isBlank(orderBy)) {
            wrapper.orderByDesc(Article::getPublishedAt).orderByDesc(Article::getId);
            return;
        }
        String column = ORDER_WHITELIST.get(orderBy.trim().toLowerCase());
        if (column == null) {
            wrapper.orderByDesc(Article::getPublishedAt).orderByDesc(Article::getId);
            return;
        }
        // column 来自白名单常量，不含用户输入
        wrapper.last("ORDER BY " + column + (asc ? " ASC" : " DESC"));
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

    /** 按差集同步文章标签关联，并维护标签的文章计数 */
    private void replaceArticleTags(Long articleId, List<Long> oldTagIds, List<Long> newTagIds) {
        Set<Long> oldSet = new LinkedHashSet<>(oldTagIds == null ? List.of() : oldTagIds);
        Set<Long> newSet = new LinkedHashSet<>(newTagIds == null ? List.of() : newTagIds);

        List<Long> toRemove = oldSet.stream().filter(id -> !newSet.contains(id)).toList();
        List<Long> toAdd = newSet.stream().filter(id -> !oldSet.contains(id)).toList();
        if (toRemove.isEmpty() && toAdd.isEmpty()) {
            return;
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
    }

    /** 在 SQL 侧增减标签文章数，避免读改写竞态 */
    private void adjustTagArticleCount(Long tagId, int delta) {
        tagMapper.update(null, new LambdaUpdateWrapper<Tag>()
                .setSql(delta > 0
                        ? "article_count = article_count + 1"
                        : "article_count = GREATEST(article_count - 1, 0)")
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
                    article.getPublishedAt()));
        }
        return vos;
    }

    /** 单篇转详情 VO，复用批量逻辑保证字段口径一致 */
    private ArticleVO toVo(Article article) {
        ArticleListVO list = toListVos(List.of(article)).get(0);
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
                article.getUpdateTime());
    }
}
