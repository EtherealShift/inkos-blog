package com.inkos.content.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文章 - 标签关联，对应表 {@code cms_article_tag}。
 *
 * <p>该表是纯关联表：无自增主键、无逻辑删除列，因此 MyBatis-Plus 的
 * 单条按主键删除 API 不适用，批量操作统一走 {@code LambdaQueryWrapper} 条件。
 */
@Getter
@Setter
@TableName("cms_article_tag")
public class ArticleTag implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableField("article_id")
    private Long articleId;

    @TableField("tag_id")
    private Long tagId;
}
