package com.inkos.content.vo;

/** 后台语句视图，包含排序和启停状态。 */
public record QuoteAdminVO(Long id, String content, String attribution, Integer sortOrder, Integer status, String headline, String description) {
}
