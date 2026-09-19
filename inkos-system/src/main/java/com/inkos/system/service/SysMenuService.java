package com.inkos.system.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.inkos.system.entity.SysMenu;
import com.inkos.system.vo.RouterVO;

import java.util.List;

/**
 * 菜单服务。
 */
public interface SysMenuService extends IService<SysMenu> {

    /**
     * 全量菜单树（后台菜单管理页使用）。
     */
    List<SysMenu> tree();

    /**
     * 构建指定用户的前端动态路由。
     */
    List<RouterVO> buildRouters(Long userId);

    /**
     * 查询用户拥有的全部菜单（含按钮）。
     */
    List<SysMenu> listByUserId(Long userId);

    /**
     * 用户可访问的权限码集合。
     */
    List<String> listPermsByUserId(Long userId);
}
