package com.inkos.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 站内问答请求。
 *
 * @param question  用户问题，必填且不超过 500 字
 * @param sessionId 会话标识，为空时由服务端生成（用于多轮上下文与落库）
 * @param stream    是否流式返回，默认 false
 */
public record AiChatRequest(@NotBlank(message = "问题不能为空")
                            @Size(max = 500, message = "问题长度不能超过 500 字")
                            String question,
                            String sessionId,
                            Boolean stream) {

    public AiChatRequest {
        stream = stream == null ? Boolean.FALSE : stream;
    }

    /** 是否要求流式返回。 */
    public boolean streamEnabled() {
        return Boolean.TRUE.equals(stream);
    }
}
