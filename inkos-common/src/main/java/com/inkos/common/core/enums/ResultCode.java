package com.inkos.common.core.enums;

import lombok.Getter;

/**
 * 统一业务状态码。
 *
 * <p>约定：2xxxx 表示成功，4xxxx 表示调用方问题，5xxxx 表示服务端问题；
 * 前三位尽量与 HTTP 状态码对齐，便于网关与前端做通用处理。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录状态已过期"),
    FORBIDDEN(403, "没有访问权限"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    CONFLICT(409, "数据已被他人修改，请刷新后重试"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),

    FAIL(500, "操作失败"),
    INTERNAL_ERROR(50000, "系统内部错误，请稍后再试"),
    SERVICE_UNAVAILABLE(50300, "服务暂时不可用"),

    // ===== 领域错误码 =====
    LOGIN_FAILED(40101, "用户名或密码错误"),
    ACCOUNT_DISABLED(40102, "账号已被禁用"),
    USERNAME_EXISTS(40901, "用户名已存在"),
    EMAIL_EXISTS(40902, "邮箱已被注册"),
    ARTICLE_NOT_FOUND(40401, "文章不存在"),
    ARTICLE_NOT_EDITABLE(40301, "无权编辑该文章");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
