package com.inkos.content.vo;

/**
 * 标签视图。
 *
 * @param id           标签 id
 * @param name         标签名称
 * @param slug         URL 标识
 * @param articleCount 关联文章数
 */
public record TagVO(
        Long id,
        String name,
        String slug,
        Integer articleCount
) {
}
