package com.inkos.content.service;

import com.inkos.common.core.domain.PageResult;
import com.inkos.content.dto.CommentForm;
import com.inkos.content.dto.CommentQuery;
import com.inkos.content.vo.CommentAdminVO;
import com.inkos.content.vo.CommentVO;

import java.util.List;

/**
 * 评论服务。
 *
 * <p>读接口只返回已通过审核的评论；写接口允许游客参与，但昵称必填。
 */
public interface CommentService {

    /**
     * 查询某篇文章的评论树。
     *
     * @param articleId     文章 id
     * @param currentUserId 当前登录用户 id，未登录传 null（用于标记「我是否点过赞」）
     */
    List<CommentVO> tree(Long articleId, Long currentUserId);

    /**
     * 发表评论或回复。
     *
     * @param currentUserId 当前登录用户 id，未登录传 null（走游客路径）
     * @return 新评论 id
     */
    Long create(CommentForm form, Long currentUserId);

    /** 后台分页查询，可按文章与审核状态过滤 */
    PageResult<CommentAdminVO> page(CommentQuery query);

    /** 审核评论：status 只接受 1（通过）或 2（拒绝） */
    void audit(Long id, Integer status);

    /** 逻辑删除评论，并同步文章评论数 */
    void delete(Long id);

    /** 统计某篇文章已通过的评论数 */
    int countApproved(Long articleId);
}
