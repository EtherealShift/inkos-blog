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
 * 评论点赞记录，对应表 {@code cms_comment_reaction}。
 *
 * <p>唯一键 {@code (comment_id, user_id)} 保证一个人对一条评论只能点一次赞。
 */
@Getter
@Setter
@TableName("cms_comment_reaction")
public class CommentReaction extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("comment_id")
    private Long commentId;

    @TableField("user_id")
    private Long userId;
}
