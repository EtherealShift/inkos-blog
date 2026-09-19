package com.inkos.system.service.impl;

import com.inkos.common.core.constant.CommonConstants;
import com.inkos.system.mapper.SysMenuMapper;
import com.inkos.system.mapper.SysUserMapper;
import com.inkos.system.service.SysPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限查询实现。
 *
 * <p>当前为「实时查库」实现：正确性优先，且用户/角色变更能立即生效。
 * 后续接入 Redis 二级缓存时，只需在本类内部加缓存与失效逻辑，调用方无感知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl implements SysPermissionService {

    /**
     * 超级管理员权限通配符。
     *
     * <p>刻意用单个 {@code *} 而不是 RuoYi 风格的 {@code *:*:*}：
     * Sa-Token 把权限项当<b>正则模式</b>匹配（{@code *} → {@code .*}），
     * {@code *:*:*} 会展开成 {@code .*:.*:.*}，要求至少两个冒号，
     * 因而匹配不到 {@code ai:chat} 这类两段式权限码 —— 超管反而被拒。
     * 单个 {@code *} 才能覆盖任意段数的权限码。
     */
    private static final String ALL_PERMISSION = "*";

    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;

    @Override
    public Set<String> getRoleCodes(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        List<String> codes = userMapper.selectRoleCodesByUserId(userId);
        // 超管始终拥有 ADMIN 角色，避免权限表误配把系统锁死
        Set<String> result = new HashSet<>(codes == null ? List.of() : codes);
        if (CommonConstants.SUPER_ADMIN_ID.equals(userId)) {
            result.add(CommonConstants.SUPER_ADMIN_ROLE);
        }
        return result;
    }

    @Override
    public Set<String> getPermissionCodes(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        if (CommonConstants.SUPER_ADMIN_ID.equals(userId)) {
            return Set.of(ALL_PERMISSION);
        }
        List<String> perms = userMapper.selectPermissionCodesByUserId(userId);
        return perms == null ? Collections.emptySet() : new HashSet<>(perms);
    }
}
