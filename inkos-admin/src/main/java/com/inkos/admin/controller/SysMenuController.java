package com.inkos.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.inkos.common.core.domain.Result;
import com.inkos.system.entity.SysMenu;
import com.inkos.system.service.SysMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单管理接口。
 */
@Tag(name = "12. 系统 - 菜单管理")
@RestController
@RequestMapping("/api/v1/admin/menus")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    @Operation(summary = "菜单树", description = "含目录、菜单与按钮，用于菜单管理页与角色授权树")
    @SaCheckPermission("system:menu:list")
    @GetMapping("/tree")
    public Result<List<SysMenu>> tree() {
        return Result.ok(menuService.tree());
    }

    @Operation(summary = "扁平菜单列表")
    @SaCheckPermission("system:menu:list")
    @GetMapping
    public Result<List<SysMenu>> list() {
        return Result.ok(menuService.list());
    }
}
