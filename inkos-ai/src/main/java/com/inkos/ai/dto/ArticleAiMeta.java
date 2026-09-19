package com.inkos.ai.dto;

import java.util.List;

/**
 * 文章 AI 元信息。由 {@code AiFacade#generateMeta} 生成，供作者确认后落库。
 *
 * <p>约定：AI 结果永远只是「候选值」，作者可修改；所有字段允许为空且不参与唯一性判断。
 *
 * @param summary         摘要，建议 ≤120 字
 * @param keywords        关键词，3~8 个
 * @param suggestedSlug   建议 URL slug（英文小写连字符）
 * @param seoDescription  SEO 描述，建议 ≤155 字
 * @param tags            标签候选
 * @param qualityScore    质量分，0~100
 * @param improvementTips 改进建议
 */
public record ArticleAiMeta(String summary,
                            List<String> keywords,
                            String suggestedSlug,
                            String seoDescription,
                            List<String> tags,
                            Integer qualityScore,
                            List<String> improvementTips) {

    public ArticleAiMeta {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        tags = tags == null ? List.of() : List.copyOf(tags);
        improvementTips = improvementTips == null ? List.of() : List.copyOf(improvementTips);
    }
}
