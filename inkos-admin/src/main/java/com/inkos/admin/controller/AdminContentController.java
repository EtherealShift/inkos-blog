package com.inkos.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.inkos.common.annotation.OperLog;
import com.inkos.common.core.domain.PageResult;
import com.inkos.common.core.domain.Result;
import com.inkos.common.core.enums.LogBusinessType;
import com.inkos.common.core.validate.ValidGroup;
import com.inkos.content.dto.ArticleForm;
import com.inkos.content.dto.ArticleQuery;
import com.inkos.content.dto.CategoryForm;
import com.inkos.content.dto.QuoteForm;
import com.inkos.content.service.ArticleService;
import com.inkos.content.service.CategoryService;
import com.inkos.content.service.QuoteService;
import com.inkos.content.vo.ArticleListVO;
import com.inkos.content.vo.ArticleVO;
import com.inkos.content.vo.CategoryVO;
import com.inkos.content.vo.QuoteAdminVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台内容接口（需登录 + 权限码）。
 */
@Tag(name = "21. 内容 - 后台", description = "文章与分类的增删改查、发布与下线")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminContentController {

    private final ArticleService articleService;
    private final CategoryService categoryService;
    private final QuoteService quoteService;

    // ==================== 文章 ====================

    @Operation(summary = "文章分页列表（全状态）")
    @SaCheckPermission("content:article:list")
    @GetMapping("/articles")
    public Result<PageResult<ArticleListVO>> articles(ArticleQuery query) {
        return Result.ok(articleService.pageAdmin(query));
    }

    @Operation(summary = "文章详情（编辑态）")
    @SaCheckPermission("content:article:query")
    @GetMapping("/articles/{id}")
    public Result<ArticleVO> detail(@PathVariable Long id) {
        return Result.ok(articleService.getByIdForEdit(id));
    }

    @Operation(summary = "新建草稿")
    @SaCheckPermission("content:article:add")
    @OperLog(title = "文章管理", businessType = LogBusinessType.INSERT)
    @PostMapping("/articles")
    public Result<Long> create(@Validated(ValidGroup.Create.class) @RequestBody ArticleForm form) {
        return Result.ok("草稿已创建", articleService.create(form));
    }

    @Operation(summary = "修改文章")
    @SaCheckPermission("content:article:edit")
    @OperLog(title = "文章管理", businessType = LogBusinessType.UPDATE)
    @PutMapping("/articles")
    public Result<Void> update(@Validated(ValidGroup.Update.class) @RequestBody ArticleForm form) {
        articleService.update(form);
        return Result.ok();
    }

    @Operation(summary = "发布文章", description = "生产环境应在发布后触发索引/缓存/MQ 流水线（见架构文档第 7 章）")
    @SaCheckPermission("content:article:publish")
    @OperLog(title = "文章管理", businessType = LogBusinessType.UPDATE)
    @PutMapping("/articles/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        articleService.publish(id);
        return Result.ok();
    }

    @Operation(summary = "下线文章")
    @SaCheckPermission("content:article:publish")
    @OperLog(title = "文章管理", businessType = LogBusinessType.UPDATE)
    @PutMapping("/articles/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        articleService.offline(id);
        return Result.ok();
    }

    @Operation(summary = "删除文章（逻辑删除）")
    @SaCheckPermission("content:article:remove")
    @OperLog(title = "文章管理", businessType = LogBusinessType.DELETE)
    @DeleteMapping("/articles/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.delete(id);
        return Result.ok();
    }

    // ==================== 分类 ====================

    @Operation(summary = "分类树（后台）")
    @SaCheckPermission("content:category:list")
    @GetMapping("/categories")
    public Result<List<CategoryVO>> categories() {
        return Result.ok(categoryService.tree());
    }

    @Operation(summary = "新增分类")
    @SaCheckPermission("content:category:add")
    @OperLog(title = "分类管理", businessType = LogBusinessType.INSERT)
    @PostMapping("/categories")
    public Result<Long> createCategory(@Validated(ValidGroup.Create.class) @RequestBody CategoryForm form) {
        return Result.ok("新增成功", categoryService.create(form));
    }

    @Operation(summary = "修改分类")
    @SaCheckPermission("content:category:edit")
    @OperLog(title = "分类管理", businessType = LogBusinessType.UPDATE)
    @PutMapping("/categories")
    public Result<Void> updateCategory(@Validated(ValidGroup.Update.class) @RequestBody CategoryForm form) {
        categoryService.update(form);
        return Result.ok();
    }

    @Operation(summary = "删除分类", description = "存在子分类时拒绝删除")
    @SaCheckPermission("content:category:remove")
    @OperLog(title = "分类管理", businessType = LogBusinessType.DELETE)
    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }

    // ==================== 首页语句 ====================

    @Operation(summary = "首页语句列表")
    @SaCheckPermission("content:quote:list")
    @GetMapping("/quotes")
    public Result<List<QuoteAdminVO>> quotes() {
        return Result.ok(quoteService.listAdmin());
    }

    @Operation(summary = "新增首页语句")
    @SaCheckPermission("content:quote:add")
    @OperLog(title = "语句管理", businessType = LogBusinessType.INSERT)
    @PostMapping("/quotes")
    public Result<Long> createQuote(@Validated(ValidGroup.Create.class) @RequestBody QuoteForm form) {
        return Result.ok("新增成功", quoteService.create(form));
    }

    @Operation(summary = "修改首页语句")
    @SaCheckPermission("content:quote:edit")
    @OperLog(title = "语句管理", businessType = LogBusinessType.UPDATE)
    @PutMapping("/quotes")
    public Result<Void> updateQuote(@Validated(ValidGroup.Update.class) @RequestBody QuoteForm form) {
        quoteService.update(form);
        return Result.ok();
    }

    @Operation(summary = "删除首页语句")
    @SaCheckPermission("content:quote:remove")
    @OperLog(title = "语句管理", businessType = LogBusinessType.DELETE)
    @DeleteMapping("/quotes/{id}")
    public Result<Void> deleteQuote(@PathVariable Long id) {
        quoteService.delete(id);
        return Result.ok();
    }
}
