package com.inkos.system.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 当前登录用户信息，供前端初始化使用。
 *
 * @param userId      用户 ID
 * @param username    登录账号
 * @param nickname    昵称
 * @param avatar      头像
 * @param email       邮箱（已脱敏）
 * @param roles       角色编码
 * @param permissions 权限码；超管返回 {@code *} 通配
 */
public record UserInfoVO(
        Long userId,
        String username,
        String nickname,
        String avatar,
        String email,
        Set<String> roles,
        Set<String> permissions
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
