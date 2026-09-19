package com.inkos.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.inkos.common.core.constant.CommonConstants;
import com.inkos.common.util.StrUtils;
import com.inkos.common.util.TreeUtils;
import com.inkos.system.entity.SysMenu;
import com.inkos.system.mapper.SysMenuMapper;
import com.inkos.system.service.SysMenuService;
import com.inkos.system.vo.MetaVO;
import com.inkos.system.vo.RouterVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    /** 顶级路由使用的布局组件 */
    private static final String COMPONENT_LAYOUT = "Layout";

    /** 有子路由但自身无组件的中间节点 */
    private static final String COMPONENT_PARENT_VIEW = "ParentView";

    @Override
    public List<SysMenu> tree() {
        List<SysMenu> all = list(Wrappers.<SysMenu>lambdaQuery()
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getSortOrder));
        return TreeUtils.build(all, SysMenu::getId, SysMenu::getParentId, SysMenu::setChildren, 0L);
    }

    @Override
    public List<RouterVO> buildRouters(Long userId) {
        List<SysMenu> menus = listRoutableMenus(userId);
        if (menus.isEmpty()) {
            return List.of();
        }
        List<SysMenu> tree = TreeUtils.build(menus, SysMenu::getId, SysMenu::getParentId, SysMenu::setChildren, 0L);
        List<RouterVO> routers = new ArrayList<>(tree.size());
        for (SysMenu menu : tree) {
            routers.add(toRouter(menu, true));
        }
        return routers;
    }

    @Override
    public List<SysMenu> listByUserId(Long userId) {
        if (CommonConstants.SUPER_ADMIN_ID.equals(userId)) {
            return list(Wrappers.<SysMenu>lambdaQuery()
                    .eq(SysMenu::getStatus, 1)
                    .orderByAsc(SysMenu::getParentId)
                    .orderByAsc(SysMenu::getSortOrder));
        }
        return baseMapper.selectAllMenusByUserId(userId);
    }

    @Override
    public List<String> listPermsByUserId(Long userId) {
        return listByUserId(userId).stream()
                .map(SysMenu::getPerms)
                .filter(StrUtils::isNotBlank)
                .distinct()
                .toList();
    }

    // ==================== 内部方法 ====================

    /**
     * 只取目录与菜单（排除按钮）用于生成路由。
     */
    private List<SysMenu> listRoutableMenus(Long userId) {
        if (CommonConstants.SUPER_ADMIN_ID.equals(userId)) {
            return list(Wrappers.<SysMenu>lambdaQuery()
                    .eq(SysMenu::getStatus, 1)
                    .in(SysMenu::getMenuType, CommonConstants.MENU_TYPE_DIR, CommonConstants.MENU_TYPE_MENU)
                    .orderByAsc(SysMenu::getParentId)
                    .orderByAsc(SysMenu::getSortOrder));
        }
        return baseMapper.selectMenusByUserId(userId);
    }

    private RouterVO toRouter(SysMenu menu, boolean topLevel) {
        RouterVO router = new RouterVO();
        // 路由名必须全局唯一；用 ID 派生可避免不同目录下同名子路由互相覆盖
        router.setName("Menu" + menu.getId());
        router.setPath(topLevel ? absolutePath(menu.getPath()) : relativePath(menu.getPath()));
        router.setComponent(resolveComponent(menu, topLevel));
        router.setHidden(!menu.isVisibleInMenu());
        router.setMeta(new MetaVO(menu.getMenuName(), menu.getIcon()));

        if (menu.getChildren() != null && !menu.getChildren().isEmpty()) {
            List<RouterVO> children = new ArrayList<>(menu.getChildren().size());
            for (SysMenu child : menu.getChildren()) {
                children.add(toRouter(child, false));
            }
            router.setChildren(children);
        }
        return router;
    }

    private String resolveComponent(SysMenu menu, boolean topLevel) {
        if (StrUtils.isNotBlank(menu.getComponent())) {
            return menu.getComponent();
        }
        // 顶级裸菜单需要外层布局承载；中间节点用 ParentView 透传
        return topLevel ? COMPONENT_LAYOUT : COMPONENT_PARENT_VIEW;
    }

    private String absolutePath(String path) {
        String trimmed = StrUtils.strip(path, "/");
        return StrUtils.isBlank(trimmed) ? "/" : "/" + trimmed;
    }

    private String relativePath(String path) {
        return StrUtils.strip(path, "/");
    }
}
