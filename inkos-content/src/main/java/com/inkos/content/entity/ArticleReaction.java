package com.inkos.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.inkos.common.core.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * 文章互动记录（点赞 / 收藏），对应表 {@code cms_article_reaction}。
 *
 * <p>点赞与收藏共用一张表，靠 {@link #type} 区分。唯一键
 * {@code (article_id, user_id, type)} 是幂等的真正保障：并发重复提交时
 * 由数据库兜住，业务层只需处理唯一键冲突。
 */
@Getter
@Setter
@TableName("cms_article_reaction")
public class ArticleReaction extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 点赞 */
    public static final int TYPE_LIKE = 1;

    /** 收藏 */
    public static final int TYPE_FAVORITE = 2;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("article_id")
    private Long articleId;

    @TableField("user_id")
    private Long userId;

    /** 1 点赞 / 2 收藏 */
    @TableField("type")
    private Integer type;
}
