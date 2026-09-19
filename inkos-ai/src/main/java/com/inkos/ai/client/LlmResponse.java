package com.inkos.ai.client;

/**
 * 一次大模型调用的出参（与具体厂商无关）。
 *
 * @param content          模型生成的正文
 * @param model            实际使用的模型名
 * @param promptTokens     输入 token 数
 * @param completionTokens 输出 token 数
 * @param latencyMs        调用耗时（毫秒）
 */
public record LlmResponse(String content,
                          String model,
                          int promptTokens,
                          int completionTokens,
                          long latencyMs) {

    /** 总 token 数，用于成本估算与配额统计。 */
    public int totalTokens() {
        return promptTokens + completionTokens;
    }

    /** 便捷构造：无耗时信息时调用。 */
    public static LlmResponse of(String content, String model, int promptTokens, int completionTokens) {
        return new LlmResponse(content, model, promptTokens, completionTokens, 0L);
    }
}
