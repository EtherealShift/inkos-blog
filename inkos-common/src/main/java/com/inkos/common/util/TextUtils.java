package com.inkos.common.util;

/**
 * 文本统计工具，供内容模块计算字数与预计阅读时长。
 */
public final class TextUtils {

    private TextUtils() {
    }

    /** 中文阅读速度：字/分钟 */
    private static final int CJK_PER_MINUTE = 400;

    /** 英文阅读速度：词/分钟 */
    private static final int WORD_PER_MINUTE = 220;

    /**
     * 统计字数：中日韩字符按字计，其余按空白分词计。
     */
    public static int wordCount(String text) {
        if (StrUtils.isBlank(text)) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                cjk++;
                inWord = false;
            } else if (Character.isLetterOrDigit(c)) {
                if (!inWord) {
                    other++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
        }
        return cjk + other;
    }

    /**
     * 预计阅读时长（分钟），最少 1 分钟。
     */
    public static int readingMinutes(String text) {
        if (StrUtils.isBlank(text)) {
            return 1;
        }
        int cjk = 0;
        int other = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                cjk++;
                inWord = false;
            } else if (Character.isLetterOrDigit(c)) {
                if (!inWord) {
                    other++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
        }
        double minutes = (double) cjk / CJK_PER_MINUTE + (double) other / WORD_PER_MINUTE;
        return Math.max(1, (int) Math.ceil(minutes));
    }

    /**
     * 去除 Markdown 语法标记，得到纯文本（用于摘要兜底与字数统计）。
     */
    public static String stripMarkdown(String markdown) {
        if (StrUtils.isBlank(markdown)) {
            return "";
        }
        return markdown
                // 代码块整体移除
                .replaceAll("(?s)```.*?```", " ")
                // 行内代码与图片
                .replaceAll("`[^`]*`", " ")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")
                // 链接保留文字
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                // 标题、引用、列表符号
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*>\\s?", "")
                .replaceAll("(?m)^\\s*[-*+]\\s+", "")
                .replaceAll("(?m)^\\s*\\d+\\.\\s+", "")
                // 强调与删除线
                .replaceAll("[*_~]{1,3}", "")
                // HTML 标签
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 生成兜底摘要：截取纯文本前 N 个字符。
     */
    public static String excerpt(String markdown, int maxLength) {
        String plain = stripMarkdown(markdown);
        if (plain.length() <= maxLength) {
            return plain;
        }
        return plain.substring(0, maxLength) + "…";
    }

    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)      // 基本汉字
                || (c >= 0x3400 && c <= 0x4DBF)  // 扩展 A
                || (c >= 0x3040 && c <= 0x30FF)  // 日文假名
                || (c >= 0xAC00 && c <= 0xD7AF); // 韩文
    }
}
