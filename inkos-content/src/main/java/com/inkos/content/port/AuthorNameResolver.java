package com.inkos.content.port;

import java.util.Collection;
import java.util.Map;

/**
 * 作者名称解析端口（Port）。
 *
 * <p>内容层需要展示作者昵称，但用户数据属于系统层。若让 inkos-content 直接依赖 inkos-system，
 * 两个业务模块就被绑死了；因此这里只声明「我需要什么」，由上层适配器实现「怎么拿」。
 *
 * <p>刻意设计为<b>批量</b>接口：逐条查询会在文章列表页造成 N+1。
 */
public interface AuthorNameResolver {

    /**
     * 批量解析作者昵称。
     *
     * @param authorIds 作者 ID 集合
     * @return 作者 ID → 昵称；查不到的 ID 可以不返回
     */
    Map<Long, String> resolveNames(Collection<Long> authorIds);
}
