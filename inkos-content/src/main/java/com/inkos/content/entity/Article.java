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
import java.time.LocalDateTime;

/**
 * 文章实体，对应表 {@code cms_article}。
 *
 * <p>公共审计字段来自 {@link BaseEntity}，此处不再重复声明。
 */
@Getter
@Setter
@TableName("cms_article")
public class Article extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 作者用户 id */
    @TableField("author_id")
    private Long authorId;

    /** 所属分类 id，可为空 */
    @TableField("category_id")
    private Long categoryId;

    @TableField("title")
    private String title;

    /** URL 短标识，全局唯一 */
    @TableField("slug")
    private String slug;

    /** 摘要，为空时由正文兜底生成 */
    @TableField("summary")
    private String summary;

    @TableField("cover_url")
    private String coverUrl;

    /** Markdown 原文（表列为 TEXT，注意勿用 LIKE 直接过滤） */
    @TableField("content_md")
    private String contentMd;

    /** 渲染后的 HTML，渲染器接入前暂存 Markdown 原文（表列为 TEXT） */
    @TableField("content_html")
    private String contentHtml;

    /** 状态，见 {@code ArticleStatus} */
    @TableField("status")
    private Integer status;

    /** 可见性：0 公开，1 私密 */
    @TableField("visibility")
    private Integer visibility;

    /** 字数（中文按字、英文按词） */
    @TableField("word_count")
    private Integer wordCount;

    /** 预计阅读时长（分钟），最少 1 */
    @TableField("reading_minutes")
    private Integer readingMinutes;

    @TableField("view_count")
    private Long viewCount;

    @TableField("like_count")
    private Integer likeCount;

    @TableField("comment_count")
    private Integer commentCount;

    /** 质量分，AI 或人工评估结果 */
    @TableField("quality_score")
    private Integer qualityScore;

    /** 是否 AI 生成 */
    @TableField("ai_generated")
    private Boolean aiGenerated;

    /** 发布时间，仅 PUBLISHED 时有值 */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /**
     * 逻辑删除标记：0 正常，1 已删除。
     *
     * <p>直接给默认值而不依赖 {@code FieldFill}，这样本模块在缺少
     * {@code MetaObjectHandler} 的场景下也不会把 null 写进 NOT NULL 列。
     */
    @TableLogic(value = "0", delval = "1")
    @TableField("deleted")
    private Integer deleted = 0;
}
