package com.inkos.admin.controller;

import com.inkos.common.core.domain.Result;
import com.inkos.content.service.ReactionService;
import com.inkos.content.vo.ReactionStateVO;
import com.inkos.framework.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 需登录的互动接口。
 *
 * <p>路径不在 {@code /api/v1/public/**} 白名单内，由 Sa-Token 拦截器强制登录 ——
 * 点赞与收藏必须绑定到具体用户才能去重，游客无从谈起。
 *
 * <p>语义为 <b>toggle</b>：同一个接口既点赞也取消，返回操作后的最新状态。
 * 相比 like / unlike 两个接口，前端少一次状态判断，也不会出现两次请求竞争。
 */
@Tag(name = "22. 互动 - 需登录", description = "文章点赞 / 收藏、评论点赞")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InteractionController {

    private final ReactionService reactionService;

    @Operation(summary = "点赞 / 取消点赞文章")
    @PostMapping("/articles/{articleId}/like")
    public Result<ReactionStateVO> toggleLike(@PathVariable Long articleId) {
        return Result.ok(reactionService.toggleLike(articleId, SecurityUtils.getUserId()));
    }

    @Operation(summary = "收藏 / 取消收藏文章")
    @PostMapping("/articles/{articleId}/favorite")
    public Result<ReactionStateVO> toggleFavorite(@PathVariable Long articleId) {
        return Result.ok(reactionService.toggleFavorite(articleId, SecurityUtils.getUserId()));
    }

    @Operation(summary = "点赞 / 取消点赞评论", description = "返回 true 表示操作后处于已点赞状态")
    @PostMapping("/comments/{commentId}/like")
    public Result<Boolean> toggleCommentLike(@PathVariable Long commentId) {
        return Result.ok(reactionService.toggleCommentLike(commentId, SecurityUtils.getUserId()));
    }
}
