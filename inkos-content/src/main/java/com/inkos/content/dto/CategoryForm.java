package com.inkos.content.dto;

import com.inkos.common.core.validate.ValidGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 分类新增 / 修改表单。
 *
 * @param id          分类 id，新增留空
 * @param parentId    父分类 id，根节点传 0 或留空
 * @param name        分类名称
 * @param slug        URL 标识，留空则由名称生成
 * @param description 描述
 * @param sortOrder   排序值，越小越靠前
 * @param status      状态：0 停用，1 启用
 */
public record CategoryForm(

        @NotNull(message = "分类 id 不能为空", groups = ValidGroup.Update.class)
        Long id,

        Long parentId,

        @NotBlank(message = "分类名称不能为空")
        @Size(max = 64, message = "分类名称长度不能超过 64")
        String name,

        @Size(max = 96, message = "slug 长度不能超过 96")
        String slug,

        @Size(max = 255, message = "描述长度不能超过 255")
        String description,

        Integer sortOrder,

        Integer status
) {
}
