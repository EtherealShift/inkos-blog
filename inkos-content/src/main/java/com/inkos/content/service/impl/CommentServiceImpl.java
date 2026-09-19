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
import com.inkos.content.dto.CommentForm;
import com.inkos.content.dto.CommentQuery;
import com.inkos.content.entity.Article;
import com.inkos.content.entity.Comment;
import com.inkos.content.entity.CommentReaction;
import com.inkos.content.enums.CommentStatus;
import com.inkos.content.mapper.ArticleMapper;
import com.inkos.content.mapper.CommentMapper;
import com.inkos.content.mapper.CommentReactionMapper;
import com.inkos.content.port.AuthorNameResolver;
import com.inkos.content.service.CommentService;
import com.inkos.content.vo.CommentAdminVO;
import com.inkos.content.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论服务实现。
 *
 * <p>三个刻意的设计取舍：
 * <ol>
 *   <li><b>树在内存里拼</b>：一次查出该文章的全部已通过评论（单篇评论量级有限），
 *       再在内存组装成树。相比递归查库或 MySQL 8 的 CTE，少一次 N+1 且不绑定具体数据库。</li>
 *   <li><b>父评论不可见时回复上浮</b>：父评论被删除或未过审时，其回复提升为顶层，
 *       而不是跟着一起消失 —— 内容一旦发布过就不该凭空蒸发。</li>
 *   <li><b>评论计数由本服务维护</b>：审核状态变化时同步增减 {@code cms_article.comment_count}，
 *       计数变化前先判断状态是否真的变了，避免重复审核导致计数漂移。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private final ArticleMapper articleMapper;
    private final CommentReactionMapper commentReactionMapper;
    private final ObjectProvider<AuthorNameResolver> authorNameResolverProvider;
    private final InkosMetrics metrics;

    /** 生产环境打开审核：新评论默认待审，不出现在前台 */
    @Value("${inkos.comment.require-audit:false}")
    private boolean requireAudit;

    @Override
    public List<CommentVO> tree(Long articleId, Long currentUserId) {
        List<Comment> comments = list(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, CommentStatus.APPROVED.getCode())
                .orderByAsc(Comment::getCreateTime)
                .orderByAsc(Comment::getId));
        if (comments.isEmpty()) {
            return List.of();
        }

        Map<Long, String> authorNames = resolveAuthorNames(comments);
        Set<Long> likedIds = likedCommentIds(comments, currentUserId);

        Map<Long, CommentVO> nodes = new LinkedHashMap<>();
        for (Comment comment : comments) {
            CommentVO vo = toVo(comment, authorNames, likedIds);
            nodes.put(vo.getId(), vo);
        }

        List<CommentVO> roots = new ArrayList<>();
        for (CommentVO node : nodes.values()) {
            Long parentId = node.getParentId();
            CommentVO parent = (parentId == null || parentId == 0L) ? null : nodes.get(parentId);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CommentForm form, Long currentUserId) {
        Article article = articleMapper.selectById(form.articleId());
        // getCode() 返回基本类型 int，必须先判空再比较，否则 Integer 拆箱会 NPE
        if (article == null || article.getStatus() == null
                || article.getStatus() != ArticleStatus.PUBLISHED.getCode()) {
            throw BusinessException.of(ResultCode.ARTICLE_NOT_FOUND);
        }
        if (currentUserId == null && StrUtils.isBlank(form.guestName())) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "游客发表评论需要填写昵称");
        }

        Comment comment = new Comment();
        comment.setArticleId(form.articleId());
        comment.setUserId(currentUserId);
        comment.setGuestName(currentUserId == null ? StrUtils.trim(form.guestName()) : null);
        comment.setContent(StrUtils.trim(form.content()));
        comment.setLikeCount(0);
        comment.setParentId(0L);
        comment.setRootId(0L);
        comment.setStatus(requireAudit ? CommentStatus.PENDING.getCode() : CommentStatus.APPROVED.getCode());

        Long parentId = form.parentId() == null ? 0L : form.parentId();
        if (parentId > 0L) {
            Comment parent = getById(parentId);
            if (parent == null || !Objects.equals(parent.getArticleId(), form.articleId())) {
                throw BusinessException.of(ResultCode.BAD_REQUEST, "被回复的评论不存在");
            }
            comment.setParentId(parent.getId());
            // rootId 只记顶层：任意深度的回复都能沿 rootId 一次查全，不必递归
            Long parentRoot = parent.getRootId();
            comment.setRootId(parentRoot == null || parentRoot == 0L ? parent.getId() : parentRoot);
        }

        save(comment);
        metrics.count(InkosMetrics.COMMENT_SUBMITTED,
                "audit", CommentStatus.isApproved(comment.getStatus()) ? "off" : "on");
        if (CommentStatus.isApproved(comment.getStatus())) {
            changeArticleCommentCount(comment.getArticleId(), 1);
        }
        return comment.getId();
    }

    @Override
    public PageResult<CommentAdminVO> page(CommentQuery query) {
        Page<Comment> result = page(new Page<>(query.safePageNum(), query.safePageSize()),
                new LambdaQueryWrapper<Comment>()
                        .eq(query.getArticleId() != null, Comment::getArticleId, query.getArticleId())
                        .eq(query.getStatus() != null, Comment::getStatus, query.getStatus())
                        .like(StrUtils.isNotBlank(query.getKeyword()), Comment::getContent, query.getKeyword())
                        .orderByDesc(Comment::getCreateTime)
                        .orderByDesc(Comment::getId));

        Map<Long, String> authorNames = resolveAuthorNames(result.getRecords());
        List<CommentAdminVO> records = result.getRecords().stream()
                .map(comment -> new CommentAdminVO(
                        comment.getId(),
                        comment.getArticleId(),
                        comment.getUserId(),
                        displayName(comment, authorNames),
                        comment.getContent(),
                        comment.getStatus(),
                        comment.getLikeCount(),
                        comment.getCreateTime()))
                .toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, Integer status) {
        if (!CommentStatus.isAuditable(status)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "审核状态只能是 1（通过）或 2（拒绝）");
        }
        Comment comment = getById(id);
        if (comment == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "评论不存在");
        }
        boolean wasApproved = CommentStatus.isApproved(comment.getStatus());
        boolean willApprove = CommentStatus.isApproved(status);
        if (wasApproved == willApprove) {
            // 状态没变就直接返回：重复审核不能让文章评论数反复增减
            return;
        }
        comment.setStatus(status);
        updateById(comment);
        changeArticleCommentCount(comment.getArticleId(), willApprove ? 1 : -1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Comment comment = getById(id);
        if (comment == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "评论不存在");
        }
        removeById(id);
        if (CommentStatus.isApproved(comment.getStatus())) {
            changeArticleCommentCount(comment.getArticleId(), -1);
        }
    }

    @Override
    public int countApproved(Long articleId) {
        return Math.toIntExact(count(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, CommentStatus.APPROVED.getCode())));
    }

    /** 计数用 GREATEST 兜底，避免并发或历史脏数据把评论数减成负数 */
    private void changeArticleCommentCount(Long articleId, int delta) {
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .setSql("comment_count = GREATEST(comment_count + (" + delta + "), 0)"));
    }

    private Map<Long, String> resolveAuthorNames(Collection<Comment> comments) {
        Set<Long> userIds = comments.stream()
                .map(Comment::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        AuthorNameResolver resolver = authorNameResolverProvider.getIfAvailable();
        if (resolver == null) {
            return Map.of();
        }
        Map<Long, String> names = resolver.resolveNames(userIds);
        return names == null ? Map.of() : names;
    }

    private Set<Long> likedCommentIds(Collection<Comment> comments, Long currentUserId) {
        if (currentUserId == null || comments.isEmpty()) {
            return Set.of();
        }
        List<Long> commentIds = comments.stream().map(Comment::getId).toList();
        return commentReactionMapper.selectList(new LambdaQueryWrapper<CommentReaction>()
                        .eq(CommentReaction::getUserId, currentUserId)
                        .in(CommentReaction::getCommentId, commentIds))
                .stream()
                .map(CommentReaction::getCommentId)
                .collect(Collectors.toSet());
    }

    private CommentVO toVo(Comment comment, Map<Long, String> authorNames, Set<Long> likedIds) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setArticleId(comment.getArticleId());
        vo.setParentId(comment.getParentId());
        vo.setUserId(comment.getUserId());
        vo.setAuthorName(comment.getUserId() == null ? null : authorNames.get(comment.getUserId()));
        vo.setGuestName(comment.getGuestName());
        vo.setContent(comment.getContent());
        vo.setLikeCount(comment.getLikeCount() == null ? 0 : comment.getLikeCount());
        vo.setLiked(likedIds.contains(comment.getId()));
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }

    private String displayName(Comment comment, Map<Long, String> authorNames) {
        if (comment.getUserId() == null) {
            return comment.getGuestName();
        }
        String name = authorNames.get(comment.getUserId());
        return name == null ? String.valueOf(comment.getUserId()) : name;
    }
}
