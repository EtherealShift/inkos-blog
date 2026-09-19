package com.inkos.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.inkos.common.core.domain.PageResult;
import com.inkos.common.core.domain.Result;
import com.inkos.content.dto.CommentQuery;
import com.inkos.content.service.CommentService;
import com.inkos.content.vo.CommentAdminVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论后台管理。
 *
 * <p>权限码延续 {@code content:xxx:yyy} 约定。超管持有通配权限 {@code *}，
 * 无需为新增权限码补种子数据即可访问。
 */
@Tag(name = "30. 互动 - 后台", description = "评论审核与删除")
@RestController
@RequestMapping("/api/v1/admin/comments")
@RequiredArgsConstructor
public class AdminCommentController {

    private final CommentService commentService;

    @Operation(summary = "评论分页列表", description = "可按文章与审核状态过滤")
    @SaCheckPermission("content:comment:list")
    @GetMapping
    public Result<PageResult<CommentAdminVO>> page(CommentQuery query) {
        return Result.ok(commentService.page(query));
    }

    @Operation(summary = "审核评论", description = "status：1 通过 / 2 拒绝")
    @SaCheckPermission("content:comment:audit")
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestParam Integer status) {
        commentService.audit(id, status);
        return Result.ok();
    }

    @Operation(summary = "删除评论", description = "逻辑删除，并同步文章评论数")
    @SaCheckPermission("content:comment:remove")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return Result.ok();
    }
}
