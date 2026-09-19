package com.inkos.system.domain;

import com.inkos.common.core.constant.CommonConstants;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 登录用户上下文，登录成功后写入 Sa-Token 会话。
 *
 * <p>实现 Serializable：切换 Redis 分布式会话后需要序列化，
 * 从第一天就满足该约束可以避免上线时才发现问题。
 */
@Getter
@Setter
@ToString
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    /** 角色编码集合，如 ROLE_ADMIN */
    private Set<String> roles = new HashSet<>();

    /** 权限码集合，如 content:article:add */
    private Set<String> permissions = new HashSet<>();

    private LocalDateTime loginTime;

    private String loginIp;

    /**
     * 是否超级管理员。超管短路所有权限校验，避免权限表配错导致锁死。
     */
    public boolean isSuperAdmin() {
        return CommonConstants.SUPER_ADMIN_ID.equals(userId)
                || roles.contains(CommonConstants.SUPER_ADMIN_ROLE);
    }
}
