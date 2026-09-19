package com.inkos.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.inkos.common.annotation.OperLog;
import com.inkos.common.core.domain.PageQuery;
import com.inkos.common.core.domain.PageResult;
import com.inkos.common.core.domain.Result;
import com.inkos.common.core.enums.LogBusinessType;
import com.inkos.system.entity.SysRole;
import com.inkos.system.service.SysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理接口。
 */
@Tag(name = "11. 系统 - 角色管理")
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @Operation(summary = "角色分页列表")
    @SaCheckPermission("system:role:list")
    @GetMapping
    public Result<PageResult<SysRole>> page(PageQuery query) {
        return Result.ok(roleService.pageRoles(query));
    }

    @Operation(summary = "全部启用角色", description = "用于用户表单的角色下拉框")
    @SaCheckPermission("system:role:list")
    @GetMapping("/options")
    public Result<List<SysRole>> options() {
        return Result.ok(roleService.listEnabled());
    }

    @Operation(summary = "角色已授权的菜单 ID")
    @SaCheckPermission("system:role:query")
    @GetMapping("/{id}/menus")
    public Result<List<Long>> menus(@PathVariable Long id) {
        return Result.ok(roleService.listMenuIdsByRoleId(id));
    }

    @Operation(summary = "新增角色")
    @SaCheckPermission("system:role:add")
    @OperLog(title = "角色管理", businessType = LogBusinessType.INSERT)
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SysRole role) {
        return Result.ok("新增成功", roleService.createRole(role));
    }

    @Operation(summary = "修改角色")
    @SaCheckPermission("system:role:edit")
    @OperLog(title = "角色管理", businessType = LogBusinessType.UPDATE)
    @PutMapping
    public Result<Void> update(@Valid @RequestBody SysRole role) {
        roleService.updateRole(role);
        return Result.ok();
    }

    @Operation(summary = "删除角色", description = "角色下仍有用户时拒绝删除")
    @SaCheckPermission("system:role:remove")
    @OperLog(title = "角色管理", businessType = LogBusinessType.DELETE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.ok();
    }

    @Operation(summary = "为角色分配菜单权限")
    @SaCheckRole("ROLE_ADMIN")
    @OperLog(title = "角色管理", businessType = LogBusinessType.GRANT)
    @PutMapping("/{id}/menus")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        roleService.assignMenus(id, menuIds);
        return Result.ok();
    }
}
