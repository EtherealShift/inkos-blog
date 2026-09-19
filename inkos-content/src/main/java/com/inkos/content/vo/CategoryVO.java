package com.inkos.content.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类视图，可承载子分类构成树。
 *
 * @param id           分类 id
 * @param parentId     父分类 id
 * @param name         分类名称
 * @param slug         URL 标识
 * @param description  描述
 * @param sortOrder    排序值
 * @param articleCount 文章数
 * @param children     子分类，可变集合以便树构建时回填
 */
public record CategoryVO(
        Long id,
        Long parentId,
        String name,
        String slug,
        String description,
        Integer sortOrder,
        Integer articleCount,
        List<CategoryVO> children
) {

    /**
     * record 的字段是 final，但集合本身可变；
     * 这里把 children 归一化为可变列表，避免回填时抛 UnsupportedOperationException。
     */
    public CategoryVO {
        children = children == null ? new ArrayList<>() : new ArrayList<>(children);
    }

    /** 追加子分类（供 TreeUtils 回填使用） */
    public void addChildren(List<CategoryVO> nodes) {
        if (nodes != null) {
            this.children.addAll(nodes);
        }
    }
}
