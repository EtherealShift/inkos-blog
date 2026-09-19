package com.inkos.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 标签实体，对应表 {@code cms_tag}。
 *
 * <p>{@code cms_tag} 没有 create_by/update_by/remark 列，因此不继承 {@code BaseEntity}，
 * 只自行声明两个时间字段并交给同一个填充器处理。
 */
@Getter
@Setter
@TableName("cms_tag")
public class Tag implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("slug")
    private String slug;

    /** 关联文章数，冗余字段 */
    @TableField("article_count")
    private Integer articleCount;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0 正常，1 已删除 */
    @TableLogic(value = "0", delval = "1")
    @TableField("deleted")
    private Integer deleted = 0;
}
