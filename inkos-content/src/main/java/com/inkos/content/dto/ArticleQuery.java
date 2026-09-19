package com.inkos.content.dto;

import com.inkos.common.core.domain.PageQuery;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;

/**
 * 文章分页查询条件。
 *
 * <p>必须是 class 而不能是 record：{@link PageQuery} 是类，record 无法继承类。
 */
@Getter
@Setter
@ToString(callSuper = true)
public class ArticleQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 按分类过滤 */
    private Long categoryId;

    /** 按标签过滤 */
    private Long tagId;

    /** 按作者过滤 */
    private Long authorId;

    /** 按状态过滤，为空表示不限（前台查询会强制覆盖为已发布） */
    private Integer status;
}
