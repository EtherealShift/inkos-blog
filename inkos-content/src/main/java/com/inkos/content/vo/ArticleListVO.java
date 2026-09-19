package com.inkos.content.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章列表项视图。
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
 */
public record ArticleListVO(
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
        LocalDateTime publishedAt
) {
}
