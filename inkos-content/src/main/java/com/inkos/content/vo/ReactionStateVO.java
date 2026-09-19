package com.inkos.content.vo;

/**
 * 一篇文章的互动状态快照。
 *
 * <p>三个计数一起返回，是为了让文章页一次请求就能渲染出全部计数与按钮的选中态，
 * 避免「点赞数一个接口、收藏数一个接口」的请求放大。
 *
 * @param likeCount     点赞总数
 * @param favoriteCount 收藏总数
 * @param commentCount  已通过审核的评论总数
 * @param liked         当前请求者是否已点赞；未登录恒为 false
 * @param favorited     当前请求者是否已收藏；未登录恒为 false
 */
public record ReactionStateVO(
        Integer likeCount,
        Integer favoriteCount,
        Integer commentCount,
        Boolean liked,
        Boolean favorited) {
}
