package com.inkos.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.inkos.common.core.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * 分类实体，对应表 {@code cms_category}。
 *
 * <p>通过 {@code parentId} 自关联形成树；根节点的 {@code parentId} 为 0。
 */
@Getter
@Setter
@TableName("cms_category")
public class Category extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 父分类 id，根节点为 0 */
    @TableField("parent_id")
    private Long parentId;

    @TableField("name")
    private String name;

    @TableField("slug")
    private String slug;

    @TableField("description")
    private String description;

    /** 排序值，越小越靠前 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 分类下文章数，冗余字段便于列表展示 */
    @TableField("article_count")
    private Integer articleCount;

    /** 状态：0 停用，1 启用 */
    @TableField("status")
    private Integer status;

    /** 逻辑删除标记：0 正常，1 已删除 */
    @TableLogic(value = "0", delval = "1")
    @TableField("deleted")
    private Integer deleted = 0;
}
