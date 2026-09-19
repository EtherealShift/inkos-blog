package com.inkos.content.service;

import com.inkos.content.dto.CategoryForm;
import com.inkos.content.vo.CategoryVO;

import java.util.List;

/**
 * 分类服务。
 */
public interface CategoryService {

    /**
     * 全量分类树，根节点的 {@code parentId} 为 0。
     *
     * @return 根分类列表
     */
    List<CategoryVO> tree();

    /**
     * 新增分类。
     *
     * @param form 表单
     * @return 新分类 id
     */
    Long create(CategoryForm form);

    /**
     * 修改分类。
     *
     * @param form 表单，必须携带 id
     */
    void update(CategoryForm form);

    /**
     * 逻辑删除分类，存在子分类时拒绝删除。
     *
     * @param id 分类 id
     */
    void delete(Long id);
}
