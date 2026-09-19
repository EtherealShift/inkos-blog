package com.inkos.framework.security;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.inkos.common.util.StrUtils;
import com.inkos.system.domain.LoginUser;
import com.inkos.system.dto.LoginRequest;
import com.inkos.system.service.SysLoginLogService;
import com.inkos.system.service.SysMenuService;
import com.inkos.system.service.SysUserService;
import com.inkos.system.vo.RouterVO;
import com.inkos.system.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证编排服务。
 *
 * <p>把「凭证校验」（system 层）与「会话签发」（Sa-Token）粘合在一起。
 * 之所以放在 framework 而不是 system，是为了让 system 层完全不依赖 Sa-Token —— 换来的是
 * 业务层可以被任意测试、也保留了将来更换鉴权方案的自由。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserService userService;
    private final SysMenuService menuService;
    private final SysLoginLogService loginLogService;

    /**
     * 登录：校验凭证 → 签发 Token → 写入会话 → 记录日志。
     */
    public LoginResult login(LoginRequest request) {
        String ip = SecurityUtils.getClientIp();
        String userAgent = SecurityUtils.getUserAgent();

        LoginUser loginUser;
        try {
            loginUser = userService.authenticate(request.username(), request.password());
        } catch (RuntimeException e) {
            // 失败也要留痕，便于识别撞库行为
            loginLogService.record(request.username(), false, e.getMessage(), ip, userAgent);
            throw e;
        }

        StpUtil.login(loginUser.getUserId(), new SaLoginParameter().setIsLastingCookie(request.remember()));

        loginUser.setLoginIp(ip);
        SecurityUtils.setLoginUser(loginUser);
        userService.updateLastLogin(loginUser.getUserId(), ip);
        loginLogService.record(loginUser.getUsername(), true, "登录成功", ip, userAgent);

        log.info("用户登录成功 userId={} username={} ip={}", loginUser.getUserId(), loginUser.getUsername(), ip);
        return new LoginResult(StpUtil.getTokenName(), StpUtil.getTokenValue(), StpUtil.getTokenTimeout());
    }

    /**
     * 注销当前会话。
     */
    public void logout() {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        if (user != null) {
            loginLogService.record(user.getUsername(), true, "退出登录", SecurityUtils.getClientIp(),
                    SecurityUtils.getUserAgent());
        }
        StpUtil.logout();
    }

    /**
     * 当前登录用户信息（前端初始化用）。
     */
    public UserInfoVO getUserInfo() {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        if (user == null) {
            // 会话可能已过期，触发一次未登录异常由全局处理器转为 401
            StpUtil.checkLogin();
        }
        return new UserInfoVO(
                user.getUserId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                null,
                user.getRoles(),
                user.getPermissions());
    }

    /**
     * 当前用户的前端动态路由。
     */
    public List<RouterVO> getRouters() {
        Long userId = SecurityUtils.getUserId();
        return menuService.buildRouters(userId);
    }

    /**
     * 判断当前请求是否已登录（供前端探活，不抛异常）。
     */
    public boolean isLogin() {
        return StrUtils.isNotBlank(StpUtil.getTokenValue()) && StpUtil.isLogin();
    }
}
