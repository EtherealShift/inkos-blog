package com.inkos.common.core.enums;

import lombok.Getter;

/**
 * 操作日志业务类型，用于 {@code @OperLog} 注解。
 */
@Getter
public enum LogBusinessType {

    OTHER("其它"),
    INSERT("新增"),
    UPDATE("修改"),
    DELETE("删除"),
    GRANT("授权"),
    EXPORT("导出"),
    IMPORT("导入"),
    FORCE("强退"),
    CLEAN("清空");

    private final String desc;

    LogBusinessType(String desc) {
        this.desc = desc;
    }
}
