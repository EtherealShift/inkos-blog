package com.inkos.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inkos.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 Mapper。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 查询用户拥有的角色编码。
     */
    @Select("""
            SELECT r.role_code
              FROM sys_role r
              JOIN sys_user_role ur ON ur.role_id = r.id
             WHERE ur.user_id = #{userId}
               AND r.status = 1
               AND r.deleted = 0
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询用户拥有的权限码（来自所有角色的按钮/菜单）。
     */
    @Select("""
            SELECT DISTINCT m.perms
              FROM sys_menu m
              JOIN sys_role_menu rm ON rm.menu_id = m.id
              JOIN sys_user_role ur ON ur.role_id = rm.role_id
             WHERE ur.user_id = #{userId}
               AND m.status = 1
               AND m.perms IS NOT NULL
               AND m.perms <> ''
            """)
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);
}
