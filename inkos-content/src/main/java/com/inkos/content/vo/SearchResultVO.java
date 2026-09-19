package com.inkos.content.vo;

/**
 * 检索结果项。
 *
 * <p>刻意组合而非继承 {@link ArticleListVO}：命中片段是检索独有的语义，
 * 把它塞进列表 VO 会让普通列表接口也背上一个永远为 null 的字段。
 *
 * @param article 文章列表视图（与其它列表接口字段完全一致）
 * @param snippet 命中片段，关键字两侧各截取一段正文；命中标题或摘要时为 null
 */
public record SearchResultVO(
        ArticleListVO article,
        String snippet) {
}
