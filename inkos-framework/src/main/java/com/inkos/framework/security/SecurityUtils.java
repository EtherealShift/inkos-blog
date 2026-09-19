package com.inkos.framework.security;

import cn.dev33.satoken.stp.StpUtil;
import com.inkos.common.core.constant.CommonConstants;
import com.inkos.common.util.StrUtils;
import com.inkos.system.domain.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 认证上下文工具。
 *
 * <p>业务代码一律通过本类获取当前用户，不要直接调用 {@code StpUtil} ——
 * 这样将来换成别的鉴权框架或调整会话结构时，改动只集中在这一处。
 */
public final class SecurityUtils {

    /** Sa-Token 会话中登录用户的 key */
    public static final String LOGIN_USER_KEY = "loginUser";

    private SecurityUtils() {
    }

    /**
     * 写入登录用户到 Sa-Token 会话。
     */
    public static void setLoginUser(LoginUser loginUser) {
        StpUtil.getSession().set(LOGIN_USER_KEY, loginUser);
    }

    /**
     * 读取当前登录用户，未登录返回 null（不抛异常，便于在非请求线程中安全调用）。
     */
    public static LoginUser getLoginUserOrNull() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            Object value = StpUtil.getSession().get(LOGIN_USER_KEY);
            return value instanceof LoginUser user ? user : null;
        } catch (Exception e) {
            // 非 Web 线程（如定时任务、MQ 消费）没有请求上下文，此处静默降级
            return null;
        }
    }

    /**
     * 读取当前登录用户，未登录返回 null。
     */
    public static LoginUser getLoginUser() {
        return getLoginUserOrNull();
    }

    /**
     * 当前用户 ID；未登录返回 null。
     */
    public static Long getUserIdOrNull() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 当前用户 ID；未登录直接抛 NotLoginException。
     */
    public static Long getUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 当前用户名；用于审计字段填充，未登录返回 "system"。
     */
    public static String getUsernameOrSystem() {
        LoginUser user = getLoginUserOrNull();
        if (user != null && StrUtils.isNotBlank(user.getUsername())) {
            return user.getUsername();
        }
        Long userId = getUserIdOrNull();
        return userId == null ? "system" : String.valueOf(userId);
    }

    public static boolean isSuperAdmin() {
        LoginUser user = getLoginUserOrNull();
        return user != null && user.isSuperAdmin();
    }

    /**
     * 当前请求的真实客户端 IP。
     *
     * <p>注意：{@code X-Forwarded-For} 可被客户端伪造，只有在受信任的反向代理后面才可信。
     * 生产环境应由 Nginx 覆写该头，并在网关层丢弃外部传入的同名头。
     */
    public static String getClientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "unknown";
        }
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (StrUtils.isNotBlank(value) && !"unknown".equalsIgnoreCase(value)) {
                // XFF 是逗号分隔链，第一个才是原始客户端
                int comma = value.indexOf(',');
                return comma > 0 ? value.substring(0, comma).trim() : value.trim();
            }
        }
        return request.getRemoteAddr();
    }

    public static String getUserAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getHeader("User-Agent");
    }

    public static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    /**
     * 判断是否为内置超级管理员 ID。
     */
    public static boolean isSuperAdminId(Long userId) {
        return CommonConstants.SUPER_ADMIN_ID.equals(userId);
    }
}
