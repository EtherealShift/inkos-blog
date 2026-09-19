package com.inkos.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 树形结构构建工具。
 *
 * <p>刻意不定义统一的 TreeNode 接口：不同场景（菜单、分类、评论）字段名各异，
 * 用函数式参数适配比强行继承一个基类更灵活，也避免基础层被业务字段污染。
 */
public final class TreeUtils {

    private TreeUtils() {
    }

    /**
     * 由扁平列表构建树（单次遍历 + 一次回填，避免 O(n²)）。
     *
     * @param nodes          扁平节点集合
     * @param idGetter       取自身 id
     * @param parentGetter   取父 id
     * @param childrenSetter 回填子节点，仅对有子节点的节点调用
     * @param rootId         根节点的父 id 值（如 0L 或 null）
     * @return 根节点列表，保持入参相对顺序
     */
    public static <T, ID> List<T> build(Collection<T> nodes,
                                        Function<T, ID> idGetter,
                                        Function<T, ID> parentGetter,
                                        BiConsumer<T, List<T>> childrenSetter,
                                        ID rootId) {
        List<T> roots = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            return roots;
        }

        Map<ID, T> indexed = new LinkedHashMap<>(nodes.size());
        for (T node : nodes) {
            indexed.put(idGetter.apply(node), node);
        }

        // 先把子节点按父 id 归拢，再统一回填。
        // 若边遍历边 set，同一父节点的第二个子节点会覆盖掉第一个。
        Map<ID, List<T>> childrenMap = new LinkedHashMap<>();
        for (T node : nodes) {
            ID parentId = parentGetter.apply(node);
            boolean isRoot = parentId == null ? rootId == null : parentId.equals(rootId);

            if (isRoot || !indexed.containsKey(parentId)) {
                // 父节点不在结果集内（如按权限过滤后的菜单）时提升为根节点，
                // 否则用户会看不到本该可见的节点。
                roots.add(node);
                continue;
            }
            childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(node);
        }

        for (T node : nodes) {
            List<T> children = childrenMap.get(idGetter.apply(node));
            if (children != null && !children.isEmpty()) {
                childrenSetter.accept(node, children);
            }
        }
        return roots;
    }
}
