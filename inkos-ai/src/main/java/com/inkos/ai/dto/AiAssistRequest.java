package com.inkos.ai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 写作助手请求。
 *
 * @param action    动作，取值 {@code outline / expand / polish / continue / title}，必填
 * @param selection 选中的原文，润色/扩写时使用
 * @param context   已有上下文（如全文或所在小节），用于保持风格一致
 */
public record AiAssistRequest(@NotBlank(message = "写作动作不能为空") String action,
                              String selection,
                              String context) {
}
