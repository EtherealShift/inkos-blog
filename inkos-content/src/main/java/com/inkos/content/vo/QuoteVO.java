package com.inkos.content.vo;

/** 公开语句及可选首屏成组文案。 */
public record QuoteVO(Long id, String content, String attribution, String headline, String description) {
    public QuoteVO(Long id, String content, String attribution) { this(id, content, attribution, null, null); }
}
