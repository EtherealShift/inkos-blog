package com.inkos.common.util;

import org.apache.commons.lang3.StringUtils;

/**
 * 字符串工具。继承 commons-lang3，只补充项目特有的方法。
 */
public final class StrUtils extends StringUtils {

    private StrUtils() {
    }

    /**
     * 由标题生成 URL slug。
     *
     * <p>中文等非 ASCII 字符会被剔除；若结果为空（标题全是中文），
     * 则退化为「p-{hash}」，保证 slug 始终非空且可寻址。
     */
    public static String slugify(String title) {
        if (isBlank(title)) {
            return "untitled";
        }
        String slug = title.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("^-+|-+$", "");
        // 纯中文标题：保留中文可读，同时拼上短哈希避免重复
        if (slug.isEmpty()) {
            return "p-" + Integer.toHexString(title.hashCode() & 0x7fffffff);
        }
        return slug.length() > 180 ? slug.substring(0, 180) : slug;
    }

    /**
     * 邮箱脱敏：{@code abcd@x.com -> a***@x.com}
     */
    public static String maskEmail(String email) {
        if (isBlank(email) || !email.contains("@")) {
            return email;
        }
        int at = email.indexOf('@');
        String name = email.substring(0, at);
        String domain = email.substring(at);
        if (name.length() <= 1) {
            return "*" + domain;
        }
        return name.charAt(0) + "***" + domain;
    }

    /** 截断字符串并追加省略号 */
    public static String abbreviate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
