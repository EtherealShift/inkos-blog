package com.inkos.common.core.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 文章状态机。
 *
 * <pre>
 *   DRAFT ──提交──▶ PENDING ──通过──▶ PUBLISHED ──下线──▶ OFFLINE
 *     ▲                 │                  │                 │
 *     └─────驳回────────┘                  └──────重新发布─────┘
 *   任意状态 ──删除──▶ RECYCLED
 * </pre>
 */
@Getter
public enum ArticleStatus {

    DRAFT(0, "草稿"),
    PENDING(1, "待审核"),
    PUBLISHED(2, "已发布"),
    OFFLINE(3, "已下线"),
    RECYCLED(4, "回收站");

    private final int code;
    private final String desc;

    ArticleStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ArticleStatus of(Integer code) {
        if (code == null) {
            return DRAFT;
        }
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .findFirst()
                .orElse(DRAFT);
    }

    /** 是否对外可见 */
    public boolean visibleToPublic() {
        return this == PUBLISHED;
    }
}
