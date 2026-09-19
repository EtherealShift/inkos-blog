package com.inkos.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.common.util.StrUtils;
import com.inkos.common.util.TreeUtils;
import com.inkos.content.dto.CategoryForm;
import com.inkos.content.entity.Category;
import com.inkos.content.mapper.CategoryMapper;
import com.inkos.content.service.CategoryService;
import com.inkos.content.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分类服务实现。
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    /** 根分类的父 id 约定值 */
    private static final Long ROOT_PARENT_ID = 0L;

    /** 启用状态 */
    private static final int STATUS_ENABLED = 1;

    @Override
    public List<CategoryVO> tree() {
        List<Category> categories = list(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId));

        List<CategoryVO> nodes = categories.stream()
                .map(category -> new CategoryVO(
                        category.getId(),
                        category.getParentId(),
                        category.getName(),
                        category.getSlug(),
                        category.getDescription(),
                        category.getSortOrder(),
                        category.getArticleCount(),
                        null))
                .toList();

        return TreeUtils.build(
                nodes,
                CategoryVO::id,
                CategoryVO::parentId,
                CategoryVO::addChildren,
                ROOT_PARENT_ID);
    }

    @Override
    public Long create(CategoryForm form) {
        Long parentId = resolveParentId(form.parentId(), null);

        Category category = new Category();
        category.setParentId(parentId);
        category.setName(StrUtils.trim(form.name()));
        category.setSlug(resolveUniqueSlug(form.slug(), form.name(), null));
        category.setDescription(StrUtils.trim(form.description()));
        category.setSortOrder(form.sortOrder() == null ? 0 : form.sortOrder());
        category.setStatus(form.status() == null ? STATUS_ENABLED : form.status());
        category.setArticleCount(0);
        save(category);
        return category.getId();
    }

    @Override
    public void update(CategoryForm form) {
        Category existing = getCategoryOrThrow(form.id());
        Long parentId = resolveParentId(form.parentId(), existing.getId());

        Category update = new Category();
        update.setId(existing.getId());
        update.setParentId(parentId);
        update.setName(StrUtils.trim(form.name()));
        update.setSlug(resolveUniqueSlug(form.slug(), form.name(), existing.getId()));
        update.setDescription(StrUtils.trim(form.description()));
        update.setSortOrder(form.sortOrder() == null ? existing.getSortOrder() : form.sortOrder());
        update.setStatus(form.status() == null ? existing.getStatus() : form.status());
        updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getCategoryOrThrow(id);

        // 先删子节点会让子孙成为孤儿，直接拒绝并提示调用方逐级清理
        boolean hasChildren = count(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, id)) > 0;
        if (hasChildren) {
            throw BusinessException.of(ResultCode.CONFLICT, "该分类下存在子分类，请先删除子分类");
        }
        removeById(id);
    }

    // ==================== 内部工具 ====================

    private Category getCategoryOrThrow(Long id) {
        if (id == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "分类不存在");
        }
        Category category = getById(id);
        if (category == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "分类不存在");
        }
        return category;
    }

    /**
     * 父分类校验：不存在则报错，且不允许把分类挂到自己名下。
     *
     * @param parentId  表单传入的父 id，为空视为根节点
     * @param selfId    当前分类 id，新增时为 null
     */
    private Long resolveParentId(Long parentId, Long selfId) {
        if (parentId == null) {
            return ROOT_PARENT_ID;
        }
        if (parentId.equals(selfId)) {
            throw BusinessException.of(ResultCode.BAD_REQUEST, "不能将分类的父级设为自身");
        }
        if (!ROOT_PARENT_ID.equals(parentId) && getById(parentId) == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "父分类不存在：" + parentId);
        }
        return parentId;
    }

    /** slug 归一化并保证唯一 */
    private String resolveUniqueSlug(String slug, String name, Long excludeId) {
        String base = StrUtils.isBlank(slug) ? StrUtils.slugify(name) : StrUtils.slugify(slug);
        String candidate = base;
        int suffix = 2;
        while (slugExists(candidate, excludeId)) {
            String tail = "-" + suffix++;
            String head = base.length() + tail.length() > 96
                    ? base.substring(0, 96 - tail.length())
                    : base;
            candidate = head + tail;
        }
        return candidate;
    }

    private boolean slugExists(String slug, Long excludeId) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
                .eq(Category::getSlug, slug);
        if (excludeId != null) {
            wrapper.ne(Category::getId, excludeId);
        }
        return baseMapper.selectCount(wrapper) > 0;
    }
}
