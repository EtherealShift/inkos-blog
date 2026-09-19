package com.inkos.ai.enums;

import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.common.util.StrUtils;
import lombok.Getter;

import java.util.Arrays;

/**
 * 对话消息角色，取值与 OpenAI Chat Completions 协议一致（小写）。
 */
@Getter
public enum AiRole {

    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool");

    private final String code;

    AiRole(String code) {
        this.code = code;
    }

    /**
     * 按 code 解析角色；为空时按 {@link #USER} 处理。
     *
     * @throws BusinessException 未知角色时抛出 400 业务异常
     */
    public static AiRole of(String code) {
        if (StrUtils.isBlank(code)) {
            return USER;
        }
        String normalized = code.trim();
        return Arrays.stream(values())
                .filter(role -> role.code.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> BusinessException.of(ResultCode.BAD_REQUEST, "未知的消息角色：" + code));
    }
}
