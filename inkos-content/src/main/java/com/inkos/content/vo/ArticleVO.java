package com.inkos.content.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章详情视图：在列表字段基础上补齐正文与编辑态信息。
 *
 * <p>不继承 {@link ArticleListVO}（record 不能继承 record），改为平铺全部字段，
 * 换取 JSON 序列化时字段顺序稳定、前端无需处理嵌套。
 *
 * @param id             文章 id
 * @param title          标题
 * @param slug           URL 标识
 * @param summary        摘要
 * @param coverUrl       封面图地址
 * @param categoryId     分类 id
 * @param categoryName   分类名称
 * @param authorId       作者 id
 * @param authorName     作者名称
 * @param tags           标签名称列表
 * @param viewCount      浏览量
 * @param likeCount      点赞数
 * @param commentCount   评论数
 * @param readingMinutes 预计阅读时长（分钟）
 * @param publishedAt    发布时间
 * @param contentMd      Markdown 原文
 * @param contentHtml    HTML 正文
 * @param status         文章状态码
 * @param visibility     可见性
 * @param aiGenerated    是否 AI 生成
 * @param qualityScore   质量分
 * @param createTime     创建时间
 * @param updateTime     更新时间
 */
public record ArticleVO(
        Long id,
        String title,
        String slug,
        String summary,
        String coverUrl,
        Long categoryId,
        String categoryName,
        Long authorId,
        String authorName,
        List<String> tags,
        Long viewCount,
        Integer likeCount,
        Integer commentCount,
        Integer readingMinutes,
        LocalDateTime publishedAt,
        String contentMd,
        String contentHtml,
        Integer status,
        Integer visibility,
        Boolean aiGenerated,
        Integer qualityScore,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
