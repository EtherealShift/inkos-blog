package com.inkos.ai.dto;

import java.util.List;

/**
 * 站内问答响应。
 *
 * @param answer           回答正文
 * @param sessionId        会话标识（服务端生成时会回传）
 * @param citations        引用来源（骨架阶段为空列表，接入检索后为命中的文章片段）
 * @param model            实际使用的模型
 * @param promptTokens     输入 token 数
 * @param completionTokens 输出 token 数
 * @param latencyMs        调用耗时（毫秒）
 */
public record AiChatResponse(String answer,
                             String sessionId,
                             List<String> citations,
                             String model,
                             Integer promptTokens,
                             Integer completionTokens,
                             Long latencyMs) {

    public AiChatResponse {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
