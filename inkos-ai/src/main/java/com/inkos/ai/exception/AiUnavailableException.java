package com.inkos.ai.exception;

import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;

import java.io.Serial;

/**
 * AI 能力不可用（模型超时、上游报错、未配置 API Key 等）。
 *
 * <p>业务码固定为 {@link ResultCode#AI_UNAVAILABLE}（50301），
 * 上层据此做「功能级降级」——隐藏 AI 入口，而不是给用户报 500。
 */
public class AiUnavailableException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AiUnavailableException(String message) {
        super(ResultCode.AI_UNAVAILABLE, message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(ResultCode.AI_UNAVAILABLE, message, cause);
    }
}
