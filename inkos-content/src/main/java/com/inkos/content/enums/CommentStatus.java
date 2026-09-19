package com.inkos.content.enums;

import lombok.Getter;

/**
 * 评论审核状态。
 *
 * <p>评论默认进入待审核还是直接可见，由 {@code inkos.comment.require-audit} 决定：
 * 开发环境关闭审核以便即写即见，生产环境打开以拦住垃圾评论。
 */
@Getter
public enum CommentStatus {

    /** 待审核，仅作者本人与后台可见 */
    PENDING(0, "待审核"),

    /** 已通过，公开可见 */
    APPROVED(1, "已通过"),

    /** 已拒绝，仅后台可见 */
    REJECTED(2, "已拒绝");

    private final int code;
    private final String desc;

    CommentStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static boolean isApproved(Integer code) {
        return code != null && code == APPROVED.code;
    }

    /** 后台审核只接受「通过」或「拒绝」两种目标状态 */
    public static boolean isAuditable(Integer code) {
        return code != null && (code == APPROVED.code || code == REJECTED.code);
    }
}
