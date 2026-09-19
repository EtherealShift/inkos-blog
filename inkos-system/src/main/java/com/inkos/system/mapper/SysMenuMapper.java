package com.inkos.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inkos.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单 Mapper。
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 查询用户可见的目录与菜单（用于生成前端路由），按层级与排序返回。
     */
    @Select("""
            SELECT DISTINCT m.*
              FROM sys_menu m
              JOIN sys_role_menu rm ON rm.menu_id = m.id
              JOIN sys_user_role ur ON ur.role_id = rm.role_id
             WHERE ur.user_id = #{userId}
               AND m.status = 1
               AND m.menu_type IN ('M', 'C')
             ORDER BY m.parent_id, m.sort_order
            """)
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);

    /**
     * 查询用户拥有的全部菜单（含按钮），用于构建权限集合。
     */
    @Select("""
            SELECT DISTINCT m.*
              FROM sys_menu m
              JOIN sys_role_menu rm ON rm.menu_id = m.id
              JOIN sys_user_role ur ON ur.role_id = rm.role_id
             WHERE ur.user_id = #{userId}
               AND m.status = 1
             ORDER BY m.parent_id, m.sort_order
            """)
    List<SysMenu> selectAllMenusByUserId(@Param("userId") Long userId);
}
