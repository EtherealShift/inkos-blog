package com.inkos.content.vo;

/** 公开语句，不暴露管理状态与审计字段。 */
public record QuoteVO(Long id, String content, String attribution) {
}
