package com.inkos.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.inkos.common.annotation.OperLog;
import com.inkos.common.core.domain.PageResult;
import com.inkos.common.core.domain.Result;
import com.inkos.common.core.enums.LogBusinessType;
import com.inkos.common.core.validate.ValidGroup;
import com.inkos.system.dto.SysUserForm;
import com.inkos.system.dto.SysUserQuery;
import com.inkos.system.service.SysUserService;
import com.inkos.system.vo.SysUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理接口。
 *
 * <p>权限校验采用「注解 + 权限码」：{@code @SaCheckPermission} 的具体判定由
 * framework 层的 {@code StpInterfaceImpl} 提供数据。
 */
@Tag(name = "10. 系统 - 用户管理")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    @Operation(summary = "用户分页列表")
    @SaCheckPermission("system:user:list")
    @GetMapping
    public Result<PageResult<SysUserVO>> page(SysUserQuery query) {
        return Result.ok(userService.pageUsers(query));
    }

    @Operation(summary = "用户详情")
    @SaCheckPermission("system:user:query")
    @GetMapping("/{id}")
    public Result<SysUserVO> detail(@PathVariable Long id) {
        return Result.ok(userService.getUserDetail(id));
    }

    @Operation(summary = "用户拥有的角色 ID")
    @SaCheckPermission("system:user:query")
    @GetMapping("/{id}/roles")
    public Result<List<Long>> roles(@PathVariable Long id) {
        return Result.ok(userService.listRoleIdsByUserId(id));
    }

    @Operation(summary = "新增用户")
    @SaCheckPermission("system:user:add")
    @OperLog(title = "用户管理", businessType = LogBusinessType.INSERT)
    @PostMapping
    public Result<Long> create(@Validated(ValidGroup.Create.class) @RequestBody SysUserForm form) {
        return Result.ok("新增成功", userService.createUser(form));
    }

    @Operation(summary = "修改用户", description = "password 留空表示不修改密码")
    @SaCheckPermission("system:user:edit")
    @OperLog(title = "用户管理", businessType = LogBusinessType.UPDATE)
    @PutMapping
    public Result<Void> update(@Validated(ValidGroup.Update.class) @RequestBody SysUserForm form) {
        userService.updateUser(form);
        return Result.ok();
    }

    @Operation(summary = "删除用户")
    @SaCheckPermission("system:user:remove")
    @OperLog(title = "用户管理", businessType = LogBusinessType.DELETE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.ok();
    }

    @Operation(summary = "重置密码")
    @SaCheckPermission("system:user:resetPwd")
    @OperLog(title = "用户管理", businessType = LogBusinessType.UPDATE, saveRequestData = false)
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestParam String password) {
        userService.resetPassword(id, password);
        return Result.ok();
    }

    @Operation(summary = "启用 / 停用用户")
    @SaCheckPermission("system:user:edit")
    @OperLog(title = "用户管理", businessType = LogBusinessType.UPDATE)
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.changeStatus(id, status);
        return Result.ok();
    }
}
