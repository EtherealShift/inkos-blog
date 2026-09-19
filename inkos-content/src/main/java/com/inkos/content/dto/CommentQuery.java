package com.inkos.content.dto;

import com.inkos.common.core.domain.PageQuery;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;

/**
 * 评论后台分页查询条件。
 */
@Getter
@Setter
@ToString(callSuper = true)
public class CommentQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 按文章过滤 */
    private Long articleId;

    /** 按审核状态过滤：0 待审核 1 已通过 2 已拒绝，为空表示不限 */
    private Integer status;
}
