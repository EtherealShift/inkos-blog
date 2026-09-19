package com.inkos.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 会话（表 ai_conversation）。
 *
 * <p>注意：本表没有 create_by / update_by / remark 列，因此**不继承** BaseEntity，
 * 时间字段由业务代码显式维护，避免自动填充写出不存在的列。
 *
 * <p>{@code scene} 存 {@code AiScene#getCode()}（如 qa），不是枚举名。
 */
@Getter
@Setter
@TableName("ai_conversation")
public class AiConversation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 归属用户，匿名场景可为空 */
    @TableField("user_id")
    private Long userId;

    /** 会话标识（前端传入或服务端生成的 UUID） */
    @TableField("session_id")
    private String sessionId;

    /** 场景 code，见 AiScene */
    private String scene;

    /** 会话标题，一般取首轮问题 */
    private String title;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
