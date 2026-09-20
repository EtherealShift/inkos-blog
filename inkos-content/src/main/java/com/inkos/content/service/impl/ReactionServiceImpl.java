package com.inkos.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inkos.common.core.domain.PageQuery;
import com.inkos.common.core.domain.PageResult;
import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.common.metrics.InkosMetrics;
import com.inkos.content.cache.ContentCache;
import com.inkos.content.entity.Article;
import com.inkos.content.entity.ArticleReaction;
import com.inkos.content.entity.Comment;
import com.inkos.content.entity.CommentReaction;
import com.inkos.content.mapper.ArticleMapper;
import com.inkos.content.mapper.ArticleReactionMapper;
import com.inkos.content.mapper.CommentMapper;
import com.inkos.content.mapper.CommentReactionMapper;
import com.inkos.content.service.ArticleService;
import com.inkos.content.service.ReactionService;
import com.inkos.content.vo.ArticleListVO;
import com.inkos.content.vo.ReactionStateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 互动服务实现。
 *
 * <p>幂等的实现方式：<b>先删、删不到再插，插入冲突就当作已存在</b>。
 * 比「先查存在与否、再决定插还是删」少一次查询，而且没有竞态窗口 ——
 * 两个并发请求最多是其中一个收到唯一键冲突，被捕获后返回一致的结果。
 *
 * <p>计数列用 {@code GREATEST(x + delta, 0)} 更新，避免任何异常路径把计数写成负数；
 * 增量以 {@code {0}} 占位符绑定为 PreparedStatement 参数，而不是拼进 SQL 文本。
 */
@Service
@RequiredArgsConstructor
public class ReactionServiceImpl implements ReactionService {

    private final ArticleMapper articleMapper;
    private final ArticleReactionMapper articleReactionMapper;
    private final CommentMapper commentMapper;
    private final CommentReactionMapper commentReactionMapper;
    private final ArticleService articleService;
    private final ContentCache contentCache;
    private final InkosMetrics metrics;

    @Override
    public ReactionStateVO state(Long articleId, Long userId) {
        return buildState(requireArticle(articleId), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReactionStateVO toggleLike(Long articleId, Long userId) {
        return toggleArticleReaction(articleId, userId, ArticleReaction.TYPE_LIKE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReactionStateVO toggleFavorite(Long articleId, Long userId) {
        return toggleArticleReaction(articleId, userId, ArticleReaction.TYPE_FAVORITE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleCommentLike(Long commentId, Long userId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "评论不存在");
        }

        int removed = commentReactionMapper.delete(new LambdaQueryWrapper<CommentReaction>()
                .eq(CommentReaction::getCommentId, commentId)
                .eq(CommentReaction::getUserId, userId));
        if (removed > 0) {
            changeCommentLikeCount(commentId, -1);
            metrics.count(InkosMetrics.REACTION_TOGGLED, "type", "comment", "action", "off");
            return false;
        }

        CommentReaction reaction = new CommentReaction();
        reaction.setCommentId(commentId);
        reaction.setUserId(userId);
        try {
            commentReactionMapper.insert(reaction);
        } catch (DuplicateKeyException e) {
            // 并发下已被另一个请求插入，视为已点赞，不重复计数
            return true;
        }
        changeCommentLikeCount(commentId, 1);
        metrics.count(InkosMetrics.REACTION_TOGGLED, "type", "comment", "action", "on");
        return true;
    }

    @Override
    public PageResult<ArticleListVO> pageFavorites(Long userId, PageQuery query) {
        // 先按收藏时间分页查互动表（它是「顺序的事实来源」），再回表取文章内容。
        // 反过来做（先查文章再筛收藏）无法正确分页。
        Page<ArticleReaction> page = articleReactionMapper.selectPage(
                new Page<>(query.safePageNum(), query.safePageSize()),
                new LambdaQueryWrapper<ArticleReaction>()
                        .eq(ArticleReaction::getUserId, userId)
                        .eq(ArticleReaction::getType, ArticleReaction.TYPE_FAVORITE)
                        .orderByDesc(ArticleReaction::getCreateTime)
                        .orderByDesc(ArticleReaction::getId));

        List<Long> articleIds = page.getRecords().stream()
                .map(ArticleReaction::getArticleId)
                .toList();
        List<ArticleListVO> records = articleService.listPublishedByIds(articleIds);
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 切换互动状态并返回变更后的结果。
     *
     * <p>文章只读一次，计数增量直接在内存里反映到返回值上 —— 原来是「toggle 读一次文章、
     * 回显状态又读一次文章」，同一行数据在一次请求里查两遍。
     * 一次 toggle 的查询数从 6 降到 4。
     */
    private ReactionStateVO toggleArticleReaction(Long articleId, Long userId, int type) {
        Article article = requireArticle(articleId);

        int removed = articleReactionMapper.delete(new LambdaQueryWrapper<ArticleReaction>()
                .eq(ArticleReaction::getArticleId, articleId)
                .eq(ArticleReaction::getUserId, userId)
                .eq(ArticleReaction::getType, type));

        int delta;
        if (removed > 0) {
            delta = -1;
            metrics.count(InkosMetrics.REACTION_TOGGLED, "type", typeName(type), "action", "off");
        } else {
            ArticleReaction reaction = new ArticleReaction();
            reaction.setArticleId(articleId);
            reaction.setUserId(userId);
            reaction.setType(type);
            try {
                articleReactionMapper.insert(reaction);
            } catch (DuplicateKeyException e) {
                // 并发下别人已经插过：状态就是「已互动」，直接回显，不要重复计数
                return buildState(article, userId);
            }
            delta = 1;
            metrics.count(InkosMetrics.REACTION_TOGGLED, "type", typeName(type), "action", "on");
        }

        changeArticleCount(articleId, type, delta);
        // 文章详情缓存里带着 like_count / favorite_count；slug 已在手里，顺手失效，不必额外查库
        contentCache.evictArticleDetail(article.getSlug());
        return buildState(applyCountDelta(article, type, delta), userId);
    }

    /**
     * 组装互动状态。
     *
     * <p>当前用户的互动类型用<b>一次查询</b>取回。原来是「点赞存在吗」+「收藏存在吗」
     * 两条 {@code EXISTS}：为两个布尔值查两次，而 {@code (article_id, user_id)} 的
     * 联合索引本来就能一次把两行都取出来。
     */
    private ReactionStateVO buildState(Article article, Long userId) {
        Set<Integer> types = userId == null ? Set.of() : reactionTypes(article.getId(), userId);
        return new ReactionStateVO(
                zeroIfNull(article.getLikeCount()),
                zeroIfNull(article.getFavoriteCount()),
                zeroIfNull(article.getCommentCount()),
                types.contains(ArticleReaction.TYPE_LIKE),
                types.contains(ArticleReaction.TYPE_FAVORITE));
    }

    private Set<Integer> reactionTypes(Long articleId, Long userId) {
        return articleReactionMapper.selectList(new LambdaQueryWrapper<ArticleReaction>()
                        .select(ArticleReaction::getType)
                        .eq(ArticleReaction::getArticleId, articleId)
                        .eq(ArticleReaction::getUserId, userId))
                .stream()
                .map(ArticleReaction::getType)
                .collect(Collectors.toSet());
    }

    /** 把刚写进数据库的计数增量同步到内存对象上，供回显使用 */
    private Article applyCountDelta(Article article, int type, int delta) {
        if (type == ArticleReaction.TYPE_LIKE) {
            article.setLikeCount(Math.max(zeroIfNull(article.getLikeCount()) + delta, 0));
        } else {
            article.setFavoriteCount(Math.max(zeroIfNull(article.getFavoriteCount()) + delta, 0));
        }
        return article;
    }

    private String typeName(int type) {
        return type == ArticleReaction.TYPE_LIKE ? "like" : "favorite";
    }

    private Article requireArticle(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw BusinessException.of(ResultCode.ARTICLE_NOT_FOUND);
        }
        return article;
    }

    /** 列名来自固定的二选一，增量走参数绑定 */
    private void changeArticleCount(Long articleId, int type, int delta) {
        String column = type == ArticleReaction.TYPE_LIKE ? "like_count" : "favorite_count";
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .setSql(column + " = GREATEST(" + column + " + {0}, 0)", delta));
    }

    private void changeCommentLikeCount(Long commentId, int delta) {
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                .eq(Comment::getId, commentId)
                .setSql("like_count = GREATEST(like_count + {0}, 0)", delta));
    }

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }
}
