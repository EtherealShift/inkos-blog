package com.inkos.admin.integration;

import com.inkos.content.port.AuthorNameResolver;
import com.inkos.system.entity.SysUser;
import com.inkos.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内容层「作者名称」端口的适配器。
 *
 * <p>放在 admin 层是刻意的：admin 同时依赖 content 与 framework(system)，
 * 是唯一有资格把两边接起来的地方。这样 content 与 system 之间保持零耦合，
 * 两个模块都能独立编译、独立测试、将来独立拆服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysUserAuthorNameResolver implements AuthorNameResolver {

    private final SysUserService userService;

    @Override
    public Map<Long, String> resolveNames(Collection<Long> authorIds) {
        if (authorIds == null || authorIds.isEmpty()) {
            return Map.of();
        }
        // 一次 IN 查询覆盖整页，避免逐条查用户造成 N+1
        return userService.listByIds(authorIds).stream()
                .filter(user -> user.getId() != null)
                .collect(Collectors.toMap(
                        SysUser::getId,
                        user -> user.getNickname() == null ? user.getUsername() : user.getNickname(),
                        (a, b) -> a));
    }
}
