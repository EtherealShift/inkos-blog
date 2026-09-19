package com.inkos.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inkos.common.core.domain.PageQuery;
import com.inkos.common.core.domain.PageResult;
import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
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

/**
 * 互动服务实现。
 *
 * <p>幂等的实现方式：<b>先删、删不到再插，插入冲突就当作已存在</b>。
 * 比「先查存在与否、再决定插还是删」少一次查询，而且没有竞态窗口 ——
 * 两个并发请求最多是其中一个收到唯一键冲突，被捕获后返回一致的结果。
 *
 * <p>计数列用 {@code GREATEST(x + delta, 0)} 更新，避免任何异常路径把计数写成负数。
 */
@Service
@RequiredArgsConstructor
public class ReactionServiceImpl implements ReactionService {

    private final ArticleMapper articleMapper;
    private final ArticleReactionMapper articleReactionMapper;
    private final CommentMapper commentMapper;
    private final CommentReactionMapper commentReactionMapper;
    private final ArticleService articleService;

    @Override
    public ReactionStateVO state(Long articleId, Long userId) {
        Article article = requireArticle(articleId);
        return new ReactionStateVO(
                zeroIfNull(article.getLikeCount()),
                zeroIfNull(article.getFavoriteCount()),
                zeroIfNull(article.getCommentCount()),
                userId != null && existsArticleReaction(articleId, userId, ArticleReaction.TYPE_LIKE),
                userId != null && existsArticleReaction(articleId, userId, ArticleReaction.TYPE_FAVORITE));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReactionStateVO toggleLike(Long articleId, Long userId) {
        toggleArticleReaction(articleId, userId, ArticleReaction.TYPE_LIKE);
        return state(articleId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReactionStateVO toggleFavorite(Long articleId, Long userId) {
        toggleArticleReaction(articleId, userId, ArticleReaction.TYPE_FAVORITE);
        return state(articleId, userId);
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

    private void toggleArticleReaction(Long articleId, Long userId, int type) {
        requireArticle(articleId);

        int removed = articleReactionMapper.delete(new LambdaQueryWrapper<ArticleReaction>()
                .eq(ArticleReaction::getArticleId, articleId)
                .eq(ArticleReaction::getUserId, userId)
                .eq(ArticleReaction::getType, type));
        if (removed > 0) {
            changeArticleCount(articleId, type, -1);
            return;
        }

        ArticleReaction reaction = new ArticleReaction();
        reaction.setArticleId(articleId);
        reaction.setUserId(userId);
        reaction.setType(type);
        try {
            articleReactionMapper.insert(reaction);
        } catch (DuplicateKeyException e) {
            return;
        }
        changeArticleCount(articleId, type, 1);
    }

    private boolean existsArticleReaction(Long articleId, Long userId, int type) {
        return articleReactionMapper.exists(new LambdaQueryWrapper<ArticleReaction>()
                .eq(ArticleReaction::getArticleId, articleId)
                .eq(ArticleReaction::getUserId, userId)
                .eq(ArticleReaction::getType, type));
    }

    private Article requireArticle(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw BusinessException.of(ResultCode.ARTICLE_NOT_FOUND);
        }
        return article;
    }

    private void changeArticleCount(Long articleId, int type, int delta) {
        String column = type == ArticleReaction.TYPE_LIKE ? "like_count" : "favorite_count";
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .setSql(column + " = GREATEST(" + column + " + (" + delta + "), 0)"));
    }

    private void changeCommentLikeCount(Long commentId, int delta) {
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                .eq(Comment::getId, commentId)
                .setSql("like_count = GREATEST(like_count + (" + delta + "), 0)"));
    }

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }
}
