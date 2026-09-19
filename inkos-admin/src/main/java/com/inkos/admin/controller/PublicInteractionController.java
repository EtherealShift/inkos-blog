package com.inkos.admin.controller;

import com.inkos.common.core.domain.Result;
import com.inkos.content.dto.CommentForm;
import com.inkos.content.service.CommentService;
import com.inkos.content.service.ReactionService;
import com.inkos.content.vo.CommentVO;
import com.inkos.content.vo.ReactionStateVO;
import com.inkos.framework.ratelimit.RateLimit;
import com.inkos.framework.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台互动接口（无需登录）。
 *
 * <p>读接口对所有人开放；发表评论允许游客参与，但必须填昵称。
 * 点赞/收藏这类需要去重的操作不在这里 —— 见 {@link InteractionController}。
 *
 * <p>未登录时也会尝试解析登录态：已登录访客能直接看到自己点过赞的评论。
 */
@Tag(name = "21. 互动 - 前台", description = "评论树、发表评论、互动状态")
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicInteractionController {

    private final CommentService commentService;
    private final ReactionService reactionService;

    @Operation(summary = "评论树", description = "只返回已通过审核的评论，按创建时间升序组树")
    @GetMapping("/articles/{articleId}/comments")
    public Result<List<CommentVO>> comments(@PathVariable Long articleId) {
        return Result.ok(commentService.tree(articleId, SecurityUtils.getUserIdOrNull()));
    }

    @Operation(summary = "发表评论或回复",
            description = "未登录为游客评论，需填 guestName；parentId 非空即为回复")
    @RateLimit(count = 5, period = 60, key = "comment.create", message = "评论发表过于频繁，请稍后再试")
    @PostMapping("/articles/{articleId}/comments")
    public Result<Long> comment(@PathVariable Long articleId,
                                @Valid @RequestBody CommentForm form) {
        // 以路径上的 articleId 为准，避免路径与请求体不一致造成的越权评论
        CommentForm normalized = new CommentForm(articleId, form.parentId(), form.content(), form.guestName());
        return Result.ok("评论已提交", commentService.create(normalized, SecurityUtils.getUserIdOrNull()));
    }

    @Operation(summary = "文章互动状态", description = "点赞数 / 收藏数 / 评论数与当前请求者的选中态")
    @GetMapping("/articles/{articleId}/reactions")
    public Result<ReactionStateVO> reactions(@PathVariable Long articleId) {
        return Result.ok(reactionService.state(articleId, SecurityUtils.getUserIdOrNull()));
    }
}
