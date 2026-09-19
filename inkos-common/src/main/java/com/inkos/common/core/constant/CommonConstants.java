package com.inkos.common.core.constant;

/**
 * 全局常量。
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    /** 统一 API 前缀 */
    public static final String API_PREFIX = "/api/v1";

    /** 认证请求头 */
    public static final String TOKEN_HEADER = "Authorization";

    /** Token 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 超级管理员用户 ID，拥有全部权限 */
    public static final Long SUPER_ADMIN_ID = 1L;

    /** 超级管理员角色编码 */
    public static final String SUPER_ADMIN_ROLE = "ROLE_ADMIN";

    /** 逻辑删除：未删除 */
    public static final int NOT_DELETED = 0;

    /** 逻辑删除：已删除 */
    public static final int DELETED = 1;

    /** 通用启用状态 */
    public static final int STATUS_ENABLED = 1;

    /** 通用禁用状态 */
    public static final int STATUS_DISABLED = 0;

    /** 菜单类型：目录 */
    public static final String MENU_TYPE_DIR = "M";

    /** 菜单类型：菜单 */
    public static final String MENU_TYPE_MENU = "C";

    /** 菜单类型：按钮 */
    public static final String MENU_TYPE_BUTTON = "F";

    /** 系统内置标识，内置数据不允许删除 */
    public static final String YES = "Y";

    public static final String NO = "N";
}
