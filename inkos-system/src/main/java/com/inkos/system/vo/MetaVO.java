package com.inkos.system.vo;

import java.io.Serial;
import java.io.Serializable;

/**
 * 路由元信息。
 *
 * @param title 菜单标题
 * @param icon  图标
 */
public record MetaVO(String title, String icon) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
