package com.inkos.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.inkos.common.core.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单 / 权限项。
 *
 * <p>目录与菜单用于生成前端路由，按钮类型承载权限码（{@code perms}）。
 * 本表无逻辑删除字段：菜单变更频率低，且删除后残留的按钮权限会造成越权风险。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 父菜单 ID，顶级为 0 */
    private Long parentId;

    private String menuName;

    /** 前端路由地址 */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 权限标识，如 content:article:add */
    private String perms;

    private String icon;

    /** M 目录 / C 菜单 / F 按钮 */
    private String menuType;

    private Integer sortOrder;

    /** 1 显示 / 0 隐藏 */
    private Integer visible;

    /** 1 启用 / 0 停用 */
    private Integer status;

    /** 非数据库字段：树形结构子节点 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private List<SysMenu> children = new ArrayList<>();

    public boolean isButton() {
        return "F".equals(menuType);
    }

    /**
     * 是否在侧边栏显示。
     *
     * <p>方法名刻意不叫 {@code isVisible()}：那会与 Lombok 为 {@code visible} 字段生成的
     * {@code getVisible()} 构成「同名属性、不同类型」的重载 getter，
     * MyBatis 反射时直接抛 ReflectionException。
     */
    public boolean isVisibleInMenu() {
        return visible == null || visible == 1;
    }
}
