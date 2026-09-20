package com.inkos.content.port;

/**
 * 当前登录用户端口。
 *
 * <p>与 {@link AuthorNameResolver} 同样的理由：{@code content} 模块刻意不依赖
 * {@code system} / {@code framework}，所以「谁在操作」不能由内容层自己回答，
 * 而是声明一个端口，由同时依赖两边的 {@code admin} 层提供适配器。
 *
 * <p>没有适配器（内容模块独立运行）或不在请求线程里（定时任务、种子数据初始化）时
 * 返回 {@code null}，由调用方决定兜底策略 —— 端口本身不猜。
 */
public interface CurrentUserProvider {

    /**
     * 当前登录用户 id。
     *
     * @return 已登录返回用户 id；未登录或无请求上下文返回 {@code null}
     */
    Long currentUserIdOrNull();
    default boolean canManageAllArticles() { return false; }
}
