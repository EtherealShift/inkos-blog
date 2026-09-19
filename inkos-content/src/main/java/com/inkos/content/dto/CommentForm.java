package com.inkos.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发表评论的表单。
 *
 * <p>{@code articleId} 不做非空校验：对外接口从路径 {@code /articles/{articleId}/comments}
 * 取文章，控制器会用它覆盖此字段。若在这里标 {@code @NotNull}，
 * 就会在控制器归一化之前把合法请求拒掉。
 *
 * @param articleId 评论所属文章；前端无需传，由路径参数覆盖
 * @param parentId  直接父评论 id，顶层评论传 null 或 0
 * @param content   纯文本内容，前端负责转义
 * @param guestName 游客昵称；已登录用户传 null，昵称取账号
 */
public record CommentForm(

        Long articleId,

        Long parentId,

        @NotBlank(message = "评论内容不能为空")
        @Size(max = 2000, message = "评论内容不能超过 2000 字")
        String content,

        @Size(max = 48, message = "游客昵称不能超过 48 字")
        String guestName) {
}
