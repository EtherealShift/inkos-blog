package com.inkos.admin.integration;

import com.inkos.content.port.CurrentUserProvider;
import com.inkos.framework.security.SecurityUtils;
import org.springframework.stereotype.Component;

/**
 * {@link CurrentUserProvider} 的 Sa-Token 适配器。
 *
 * <p>放在 {@code admin} 层的理由与 {@code SysUserAuthorNameResolver} 一致：
 * {@code content} 只声明端口、不依赖 {@code framework}；{@code admin} 是唯一同时
 * 依赖两边的层，由它把「当前登录用户」这个概念接起来。
 *
 * <p>这里刻意不做任何缓存 —— 登录态本来就在 Sa-Token 会话里，取一次是内存操作。
 */
@Component
public class SecurityCurrentUserProvider implements CurrentUserProvider {

    @Override
    public boolean canManageAllArticles() { return SecurityUtils.isSuperAdmin(); }

    @Override
    public Long currentUserIdOrNull() {
        // SecurityUtils 内部已经处理了「非 Web 线程没有请求上下文」的情况，会返回 null
        return SecurityUtils.getUserIdOrNull();
    }
}
