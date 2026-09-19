package com.inkos.system.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 前端动态路由节点，结构对齐 vue-router 的 RouteRecordRaw。
 */
@Getter
@Setter
@ToString
public class RouterVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 路由名称，需唯一 */
    private String name;

    /** 路由路径 */
    private String path;

    /** 组件路径；目录节点为 ParentView，顶级包装为 Layout */
    private String component;

    /** 是否在侧边栏隐藏 */
    private Boolean hidden = Boolean.FALSE;

    private MetaVO meta;

    private List<RouterVO> children = new ArrayList<>();

    public RouterVO() {
    }

    public RouterVO(String name, String path, String component) {
        this.name = name;
        this.path = path;
        this.component = component;
    }
}
