package com.inkos.content.service;

import com.inkos.content.vo.ReactionStateVO;

/**
 * 文章与评论的互动服务（点赞 / 收藏）。
 *
 * <p>所有 toggle 方法都要求已登录：游客点赞无法去重，也没有「我的收藏」可归集。
 */
public interface ReactionService {

    /** 查询一篇文章的互动快照；userId 为 null 时只返回计数，选中态恒为 false */
    ReactionStateVO state(Long articleId, Long userId);

    /** 切换点赞，返回切换后的快照 */
    ReactionStateVO toggleLike(Long articleId, Long userId);

    /** 切换收藏，返回切换后的快照 */
    ReactionStateVO toggleFavorite(Long articleId, Long userId);

    /**
     * 切换评论点赞。
     *
     * @return true 表示操作后处于「已点赞」状态
     */
    boolean toggleCommentLike(Long commentId, Long userId);
}
