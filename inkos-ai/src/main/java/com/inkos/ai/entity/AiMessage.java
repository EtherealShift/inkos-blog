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
 * AI 消息（表 ai_message）。
 *
 * <p>{@code role} 存 {@code AiRole#getCode()}（小写）；{@code citations} 存 JSON 文本，需要时由上层反序列化。
 */
@Getter
@Setter
@TableName("ai_message")
public class AiMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属会话 id */
    @TableField("conversation_id")
    private Long conversationId;

    /** 角色 code：system / user / assistant / tool */
    private String role;

    /** 消息正文 */
    private String content;

    /** 引用来源，JSON 文本 */
    private String citations;

    /** 实际使用的模型名 */
    private String model;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("latency_ms")
    private Integer latencyMs;

    /** 结束原因，如 stop / length */
    @TableField("finish_reason")
    private String finishReason;

    @TableField("create_time")
    private LocalDateTime createTime;
}
