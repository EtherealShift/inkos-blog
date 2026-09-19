package com.inkos.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 用量计量（表 ai_usage）。
 *
 * <p>维度为「用户 + 日期 + 场景 + 模型」，每次调用累加一行；
 * 金额用微元整数（1 元 = 1_000_000 微元）存储，避免浮点误差。
 */
@Getter
@Setter
@TableName("ai_usage")
public class AiUsage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 归属用户，匿名场景可为空 */
    @TableField("user_id")
    private Long userId;

    /** 统计日期 */
    @TableField("usage_date")
    private LocalDate usageDate;

    /** 场景 code，见 AiScene */
    private String scene;

    /** 模型名 */
    private String model;

    /** 调用次数 */
    @TableField("request_count")
    private Integer requestCount;

    @TableField("prompt_tokens")
    private Long promptTokens;

    @TableField("completion_tokens")
    private Long completionTokens;

    /** 估算成本（微元） */
    @TableField("cost_micro")
    private Long costMicro;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
