package com.inkos.content.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论树节点。
 *
 * <p>刻意做成可变的 class 而非 record：树是自底向上组装的，
 * record 的不可变语义会让「先建节点、再挂子节点」变成一堆重建拷贝。
 */
@Getter
@Setter
public class CommentVO {

    private Long id;

    private Long articleId;

    private Long parentId;

    /** 登录用户 id，游客评论为 null */
    private Long userId;

    /** 登录用户昵称，由 AuthorNameResolver 解析；解析不到时为 null */
    private String authorName;

    /** 游客昵称，登录用户为 null */
    private String guestName;

    private String content;

    private Integer likeCount;

    /** 当前请求者是否已点赞；未登录时恒为 false */
    private Boolean liked;

    private LocalDateTime createTime;

    /** 子回复，按创建时间升序 */
    private List<CommentVO> children = new ArrayList<>();
}
