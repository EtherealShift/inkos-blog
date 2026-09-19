package com.inkos.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.inkos.common.core.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * 评论实体，对应表 {@code cms_comment}。
 *
 * <p>两级结构：{@code parentId} 指向直接父评论，{@code rootId} 指向顶层评论，
 * 使任意深度的回复都能一次查出。
 */
@Getter
@Setter
@TableName("cms_comment")
public class Comment extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("article_id")
    private Long articleId;

    /** 登录用户 id，游客评论为空 */
    @TableField("user_id")
    private Long userId;

    /** 游客昵称，登录用户为空 */
    @TableField("guest_name")
    private String guestName;

    /** 直接父评论 id，顶层评论为 0 */
    @TableField("parent_id")
    private Long parentId;

    /** 顶层评论 id，顶层评论为 0 */
    @TableField("root_id")
    private Long rootId;

    @TableField("content")
    private String content;

    /** 状态：0 待审核，1 已通过，2 已拒绝 */
    @TableField("status")
    private Integer status;

    @TableField("like_count")
    private Integer likeCount;

    /** 逻辑删除标记：0 正常，1 已删除 */
    @TableLogic(value = "0", delval = "1")
    @TableField("deleted")
    private Integer deleted = 0;
}
