package com.inkos.system.service;

import java.util.Set;

/**
 * 权限查询服务。是 Sa-Token {@code StpInterface} 的数据来源。
 */
public interface SysPermissionService {

    /**
     * 查询用户的角色编码集合。
     */
    Set<String> getRoleCodes(Long userId);

    /**
     * 查询用户的权限码集合。超级管理员返回通配符 {@code *:*:*}。
     */
    Set<String> getPermissionCodes(Long userId);
}
