package com.inkos.content.vo;

import java.time.LocalDateTime;

/**
 * 评论后台列表项。
 *
 * @param id         评论 id
 * @param articleId  所属文章 id
 * @param userId     评论人用户 id，游客为 null
 * @param authorName 评论人展示名（登录用户取昵称，游客取 guestName）
 * @param content    评论正文
 * @param status     0 待审核 / 1 已通过 / 2 已拒绝
 * @param likeCount  点赞数
 * @param createTime 评论时间
 */
public record CommentAdminVO(
        Long id,
        Long articleId,
        Long userId,
        String authorName,
        String content,
        Integer status,
        Integer likeCount,
        LocalDateTime createTime) {
}
