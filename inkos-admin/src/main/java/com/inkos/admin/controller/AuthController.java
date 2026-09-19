package com.inkos.admin.controller;

import com.inkos.common.annotation.OperLog;
import com.inkos.common.core.domain.Result;
import com.inkos.common.core.enums.LogBusinessType;
import com.inkos.framework.ratelimit.RateLimit;
import com.inkos.framework.security.AuthService;
import com.inkos.framework.security.LoginResult;
import com.inkos.system.dto.LoginRequest;
import com.inkos.system.vo.RouterVO;
import com.inkos.system.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口。
 */
@Tag(name = "01. 认证", description = "登录、登出、当前用户信息与动态路由")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录", description = "成功后返回 Sa-Token 令牌，前端放入 Authorization 头")
    @RateLimit(count = 10, period = 60, key = "auth.login", message = "登录尝试过于频繁，请稍后再试")
    @PostMapping("/login")
    public Result<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok("登录成功", authService.login(request));
    }

    @Operation(summary = "登出")
    @OperLog(title = "用户登出", businessType = LogBusinessType.OTHER, saveRequestData = false)
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    @Operation(summary = "当前登录用户信息", description = "含角色与权限码，供前端做按钮级权限控制")
    @GetMapping("/info")
    public Result<UserInfoVO> info() {
        return Result.ok(authService.getUserInfo());
    }

    @Operation(summary = "当前用户的动态路由")
    @GetMapping("/routers")
    public Result<List<RouterVO>> routers() {
        return Result.ok(authService.getRouters());
    }

    @Operation(summary = "登录状态探活")
    @GetMapping("/status")
    public Result<Boolean> status() {
        return Result.ok(authService.isLogin());
    }
}
